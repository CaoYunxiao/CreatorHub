package com.creatorhub.backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.creatorhub.backend.dto.WorkImageResponse;
import com.creatorhub.backend.entity.UserAccount;
import com.creatorhub.backend.entity.WorkDetail;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class WorkImageTests {
    @TempDir static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("creatorhub.upload-directory", () -> uploads.toString());
    }

    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean WorkRepository works;
    @MockitoBean WorkImageRepository images;

    @Test
    void authorUploadsAndAnonymousCanReadOnlyTheLinkedImage() throws Exception {
        givenWorkAndUser(9);
        byte[] png = png(2);
        when(images.insert(eq(7L), anyString())).thenReturn(11L);
        var response = mvc.perform(multipart("/api/works/7/images")
                        .file(new MockMultipartFile("file", "../untrusted.txt", "text/plain", png))
                        .with(user("author")).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(11))
                .andReturn().getResponse();
        String url = response.getHeader("Location");
        assertNotNull(url);
        assertTrue(url.matches("/api/works/7/images/[0-9a-f]{32}\\.png"));
        verify(images).insert(7L, url);
        when(images.findByWorkId(7L)).thenReturn(List.of(new WorkImageResponse(11, url, 0)));
        when(images.isLinked(7L, url)).thenReturn(true);

        mvc.perform(get("/api/works/7/images")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value(url));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png));
        mvc.perform(get(url.replace("/works/7/", "/works/8/"))).andExpect(status().isNotFound());
    }

    @Test
    void wrongAuthorOrMissingCsrfCannotStoreImages() throws Exception {
        givenWorkAndUser(12);
        var file = new MockMultipartFile("file", "example.png", "image/png", png(2));
        mvc.perform(multipart("/api/works/7/images").file(file).with(user("author")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/works/7/images").file(file).with(user("author")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(images);
    }

    @Test
    void rejectsFakeImagesAndOverwideImages() throws Exception {
        givenWorkAndUser(9);
        for (byte[] bytes : List.of(new byte[]{1, 2, 3}, png(4097))) {
            mvc.perform(multipart("/api/works/7/images")
                            .file(new MockMultipartFile("file", "fake.png", "image/png", bytes))
                            .with(user("author")).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(images);
    }

    @Test
    void rejectsUploadsOverTheByteLimitBeforeStoringAnything() throws Exception {
        givenWorkAndUser(9);
        long before = countFiles();
        var file = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        mvc.perform(multipart("/api/works/7/images").file(file).with(user("author")).with(csrf()))
                .andExpect(status().is(413));
        verifyNoInteractions(images);
        assertEquals(before, countFiles());
    }

    @Test
    void cleansUpOnlyTheNewFileWhenTheDatabaseWriteFails() throws Exception {
        givenWorkAndUser(9);
        long before = countFiles();
        when(images.insert(eq(7L), anyString())).thenAnswer(invocation -> {
            assertEquals(before + 1, countFiles());
            throw new DataAccessResourceFailureException("simulated write failure");
        });
        mvc.perform(multipart("/api/works/7/images")
                        .file(new MockMultipartFile("file", "example.png", "image/png", png(2)))
                        .with(user("author")).with(csrf()))
                .andExpect(status().isServiceUnavailable());
        assertEquals(before, countFiles());
    }

    private void givenWorkAndUser(long userId) {
        var time = LocalDateTime.of(2026, 11, 12, 12, 0);
        when(works.findById(7L)).thenReturn(Optional.of(new WorkDetail(7, 9, "标题", null, "正文", time, time)));
        when(users.findByUsername("author")).thenReturn(Optional.of(new UserAccount(userId, "author", "test-hash")));
    }

    private long countFiles() throws IOException {
        try (var files = Files.list(uploads)) {
            return files.count();
        }
    }

    private static byte[] png(int width) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            assertTrue(ImageIO.write(new BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB), "png", output));
        }
        return bytes.toByteArray();
    }
}
