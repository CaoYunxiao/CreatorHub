package com.creatorhub.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.creatorhub.backend.dto.WorkImageResponse;
import com.creatorhub.backend.entity.WorkDetail;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.WorkForbiddenException;
import com.creatorhub.backend.exception.WorkNotFoundException;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkImageService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");

    private final Path uploadDirectory;
    private final WorkRepository works;
    private final WorkImageRepository images;
    private final UserService users;

    public WorkImageService(@Value("${creatorhub.upload-directory:uploads}") String uploadDirectory,
                            WorkRepository works, WorkImageRepository images, UserService users) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.works = works;
        this.images = images;
        this.users = users;
    }

    public WorkImageResponse upload(long workId, String username, MultipartFile file) {
        var work = requireWork(workId);
        if (work.userId() != users.currentUser(username).id()) {
            throw new WorkForbiddenException();
        }

        byte[] bytes = readUpload(file);
        String extension = imageExtension(bytes);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String imageUrl = imageUrl(workId, fileName);
        Path target = uploadDirectory.resolve(fileName);

        boolean created = false;
        try {
            Files.createDirectories(uploadDirectory);
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(bytes);
            }
            return new WorkImageResponse(images.insert(workId, imageUrl), imageUrl, 0);
        } catch (IOException exception) {
            if (created) {
                cleanup(target, exception);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片保存失败", exception);
        } catch (RuntimeException exception) {
            if (created) {
                cleanup(target, exception);
            }
            throw exception;
        }
    }

    public List<WorkImageResponse> list(long workId) {
        requireWork(workId);
        return images.findByWorkId(workId);
    }

    public ImageData read(long workId, String fileName) {
        if (workId < 1) {
            throw new InvalidRequestException("作品 ID 必须大于等于 1");
        }
        if (!FILE_NAME.matcher(fileName).matches() || !images.isLinked(workId, imageUrl(workId, fileName))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }

        Path target = uploadDirectory.resolve(fileName);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片文件大小异常");
            }
            MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return new ImageData(bytes, type);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片读取失败", exception);
        }
    }

    private WorkDetail requireWork(long workId) {
        if (workId < 1) {
            throw new InvalidRequestException("作品 ID 必须大于等于 1");
        }
        return works.findById(workId).orElseThrow(WorkNotFoundException::new);
    }

    private static byte[] readUpload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidRequestException("请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "图片不能超过 5 MiB");
        }
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "图片不能超过 5 MiB");
            }
            return bytes;
        } catch (IOException exception) {
            throw new InvalidRequestException("无法读取上传的图片");
        }
    }

    private static String imageExtension(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidRequestException("请选择有效的 JPEG 或 PNG 图片");
            }
            var reader = readers.next();
            try {
                String extension = switch (reader.getFormatName()) {
                    case "JPEG", "jpeg" -> ".jpg";
                    case "PNG", "png" -> ".png";
                    default -> throw new InvalidRequestException("仅支持 JPEG 和 PNG 图片");
                };
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new InvalidRequestException("图片宽度和高度均不得超过 4096 像素");
                }
                reader.read(0);
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new InvalidRequestException("图片损坏或无法解码");
        }
    }

    private static String imageUrl(long workId, String fileName) {
        return "/api/works/" + workId + "/images/" + fileName;
    }

    private static void cleanup(Path target, Exception originalFailure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            originalFailure.addSuppressed(exception);
        }
    }

    public record ImageData(byte[] bytes, MediaType contentType) {
    }
}
