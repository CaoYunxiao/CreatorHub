const byId = id => document.getElementById(id);
const pageSize = 12;
const maxPage = 2_147_483_647;
const validId = value => /^[1-9]\d*$/.test(String(value));
const loginDialog = byId("login-dialog");
const loginForm = byId("login-form");
const notices = byId("notice");
const tagSelect = byId("tag");
let user = null;
let activeRead;
let previousList = "#works";
let mutationPending = false;

class ApiError extends Error {
    constructor(status) {
        super({
            400: "请求参数无效。",
            401: "请登录后继续。",
            403: "操作被拒绝，请刷新后重试。",
            404: "找不到这件作品。",
            503: "数据库暂时不可用，请稍后重试。"
        }[status] ?? "暂时无法完成请求，请稍后重试。");
        this.status = status;
    }
}

async function request(path, options = {}) {
    const response = await fetch(path, { credentials: "same-origin", ...options });
    if (!response.ok) throw new ApiError(response.status);
    return response.status === 204 ? null : response.json();
}

async function write(path, method, body) {
    // 登录和退出会改变会话，每次写入前从当前会话获取 token。
    const { headerName, token } = await request("/api/auth/csrf");
    return request(path, { method, headers: { [headerName]: token }, body });
}

function node(tag, value, className) {
    const element = document.createElement(tag);
    if (value !== undefined && value !== null) element.textContent = value;
    if (className) element.className = className;
    return element;
}

function action(label, callback, secondary = false) {
    const button = node("button", label, secondary ? "secondary" : "");
    button.type = "button";
    button.addEventListener("click", () => callback(button));
    return button;
}

function announce(message = "") {
    notices.textContent = message;
    notices.hidden = !message;
}

function errorMessage(error) {
    return error instanceof ApiError ? error.message : "无法连接服务，请检查后端是否正在运行。";
}

function authUi() {
    byId("current-user").textContent = user?.username ?? "";
    byId("current-user").hidden = !user;
    byId("login").hidden = !!user;
    byId("logout").hidden = !user;
}

async function refreshUser() {
    try {
        user = await request("/api/auth/me");
    } catch (error) {
        if (error.status !== 401) throw error;
        user = null;
    }
    authUi();
}

function openLogin() {
    if (loginDialog.open) return;
    byId("login-error").hidden = true;
    loginForm.reset();
    loginDialog.showModal();
    byId("username").focus();
}

function route() {
    const [path, query] = location.hash.slice(1).split("?", 2);
    const params = new URLSearchParams(query);
    const page = Number(params.get("page"));
    if (/^work\/[1-9]\d*$/.test(path)) return { view: "detail", id: path.slice(5) };
    const normalized = { view: path === "favorites" ? "favorites" : "works",
        page: Number.isInteger(page) && page >= 1 && page <= maxPage ? page : 1 };
    if (normalized.view === "works") {
        normalized.q = [...(params.get("q") ?? "").trim()].slice(0, 200).join("");
        normalized.tagId = validId(params.get("tagId")) ? params.get("tagId") : "";
    }
    return normalized;
}

function listHash(view, page, q = "", tagId = "") {
    const params = new URLSearchParams();
    if (page > 1) params.set("page", page);
    if (q) params.set("q", q);
    if (tagId) params.set("tagId", tagId);
    const suffix = params.toString();
    return "#" + view + (suffix ? "?" + suffix : "");
}

function showView(view) {
    for (const name of ["works", "detail", "favorites"]) {
        byId(name + "-view").hidden = view !== name;
    }
    byId("works-nav").removeAttribute("aria-current");
    byId("favorites-nav").removeAttribute("aria-current");
    if (view === "works") byId("works-nav").setAttribute("aria-current", "page");
    if (view === "favorites") byId("favorites-nav").setAttribute("aria-current", "page");
}

function selectTag(id) {
    const pending = tagSelect.querySelector("[data-current-filter]");
    if (pending) pending.remove();
    if (id && !Array.from(tagSelect.options).some(option => option.value === id)) {
        const option = node("option", "标签 #" + id);
        option.value = id;
        option.setAttribute("data-current-filter", "");
        tagSelect.append(option);
    }
    tagSelect.value = id;
}

async function loadTags() {
    const tags = await request("/api/tags");
    for (const tag of tags) {
        const option = node("option", tag.name);
        option.value = tag.id;
        tagSelect.append(option);
    }
    const current = route();
    if (current.view === "works") selectTag(current.tagId);
}

function workCard(work, isFavoriteList) {
    const card = node("article", null, "card");
    const heading = node("h2");
    const link = node("a", work.title);
    link.href = "#work/" + work.id;
    heading.append(link);
    card.append(heading);
    if (work.intro) card.append(node("p", work.intro));
    const footer = node("div", null, "card-actions");
    footer.append(node("span", "作者 #" + work.authorId, "meta"));
    if (isFavoriteList) {
        footer.append(action("取消收藏", button => changeFavorite(work.id, false, button), true));
    }
    card.append(footer);
    return card;
}

async function renderList(current, signal) {
    const { view, page, q = "", tagId = "" } = current;
    const isFavorites = view === "favorites";
    const container = byId(view + "-results");
    if (!isFavorites) {
        byId("keyword").value = q;
        selectTag(tagId);
    }
    container.replaceChildren(node("p", "正在加载…", "meta"));
    if (isFavorites && !user) {
        container.replaceChildren(node("p", "登录后即可查看你的收藏。"), action("登录", openLogin));
        return;
    }
    const params = new URLSearchParams({ page, size: pageSize });
    if (q) params.set("q", q);
    if (tagId) params.set("tagId", tagId);
    const api = isFavorites ? "/api/me/favorites" : "/api/works";
    try {
        const result = await request(api + "?" + params, { signal });
        if (signal.aborted) return;
        container.replaceChildren();
        if (!result.items.length) {
            container.append(node("p", isFavorites && page === 1 ? "还没有收藏的作品。" : "这一页没有作品。", "empty"));
        } else {
            const grid = node("div", null, "grid");
            grid.append(...result.items.map(work => workCard(work, isFavorites)));
            container.append(grid);
        }
        const pagination = node("div", null, "pagination");
        if (page > 1) {
            const previous = node("a", "上一页");
            previous.href = listHash(view, page - 1, q, tagId);
            pagination.append(previous);
        }
        pagination.append(node("span", "第 " + page + " 页", "meta"));
        if (result.items.length === pageSize && page < maxPage) {
            const next = node("a", "下一页");
            next.href = listHash(view, page + 1, q, tagId);
            pagination.append(next);
        }
        container.append(pagination);
    } catch (error) {
        if (signal.aborted) return;
        handleExpiredSession(error);
        container.replaceChildren(node("p", errorMessage(error), "error"),
            action(isFavorites && error.status === 401 ? "登录" : "重试",
                isFavorites && error.status === 401 ? openLogin : () => render(), true));
    }
}

async function renderDetail(id, signal, focusHeading) {
    const container = byId("detail-content");
    byId("detail-back").href = previousList;
    byId("detail-back").textContent = previousList.startsWith("#favorites") ? "← 返回我的收藏" : "← 返回作品列表";
    const loading = node("p", "正在加载作品…", "meta");
    loading.id = "detail-title";
    container.replaceChildren(loading);
    const path = "/api/works/" + id;
    try {
        const work = await request(path, { signal });
        if (signal.aborted) return;
        const card = node("article", null, "detail");
        const top = node("div", null, "detail-head");
        const title = node("h1", work.title);
        title.id = "detail-title";
        title.tabIndex = -1;
        top.append(title, action(user ? "收藏作品" : "登录后收藏", button =>
            user ? changeFavorite(id, true, button) : openLogin()));
        card.append(top, node("p", "作者 #" + work.authorId, "meta"));
        if (work.intro) card.append(node("p", work.intro, "detail-intro"));
        card.append(node("p", work.content, "detail-body"));
        const extras = node("div");
        extras.append(node("p", "正在加载标签和图片…", "meta"));
        card.append(extras);
        container.replaceChildren(card);
        if (focusHeading) title.focus();
        const [tags, images] = await Promise.allSettled([
            request(path + "/tags", { signal }), request(path + "/images", { signal })
        ]);
        if (signal.aborted) return;
        extras.replaceChildren();
        if (tags.status === "fulfilled" && tags.value.length) {
            const pills = node("div", null, "pill-list");
            for (const tag of tags.value) {
                const link = node("a", tag.name, "pill");
                link.href = listHash("works", 1, "", String(tag.id));
                pills.append(link);
            }
            extras.append(pills);
        } else if (tags.status === "rejected") {
            extras.append(node("p", "标签暂时无法加载。", "error"));
        }
        if (images.status === "fulfilled" && images.value.length) {
            const gallery = node("div", null, "grid gallery");
            for (const [index, image] of images.value.entries()) {
                const url = String(image.imageUrl);
                if (!url.startsWith(path + "/images/") ||
                        !/^[0-9a-f]{32}\.(png|jpg)$/.test(url.slice((path + "/images/").length))) continue;
                const figure = node("figure");
                const picture = node("img");
                picture.src = url;
                picture.alt = work.title + " · 图片 " + (index + 1);
                picture.loading = "lazy";
                const link = node("a", "查看原图");
                link.href = url;
                figure.append(picture, link);
                gallery.append(figure);
            }
            extras.append(gallery);
        } else if (images.status === "rejected") {
            extras.append(node("p", "图片暂时无法加载。", "error"));
        }
    } catch (error) {
        if (signal.aborted) return;
        const heading = node("h1", "无法打开作品");
        heading.id = "detail-title";
        container.replaceChildren(heading, node("p", errorMessage(error), "error"),
            action("重试", () => render(), true));
    }
}

async function render(focusHeading = false) {
    activeRead?.abort();
    activeRead = new AbortController();
    const { signal } = activeRead;
    const current = route();
    showView(current.view);
    if (current.view === "detail") {
        await renderDetail(current.id, signal, focusHeading);
    } else {
        previousList = listHash(current.view, current.page, current.q, current.tagId);
        if (focusHeading) byId(current.view + "-title").focus();
        await renderList(current, signal);
    }
}

function handleExpiredSession(error) {
    if (error.status === 401) {
        user = null;
        authUi();
    }
}

async function changeFavorite(id, add, button) {
    if (!validId(id) || mutationPending) return;
    mutationPending = true;
    button.disabled = true;
    try {
        await write("/api/works/" + id + "/favorite", add ? "PUT" : "DELETE");
        announce(add ? "已加入收藏。" : "已取消收藏。");
        if (!add && route().view === "favorites") await render(true);
    } catch (error) {
        handleExpiredSession(error);
        announce(errorMessage(error));
        if (!user) {
            await render();
            openLogin();
        }
    } finally {
        mutationPending = false;
        button.disabled = false;
    }
}

byId("login").addEventListener("click", openLogin);
byId("cancel-login").addEventListener("click", () => loginDialog.close());
loginDialog.addEventListener("close", () => loginForm.reset());
loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (mutationPending) return;
    const button = event.submitter;
    const errorBox = byId("login-error");
    errorBox.hidden = true;
    mutationPending = true;
    button.disabled = true;
    try {
        await write("/api/auth/login", "POST", new URLSearchParams(new FormData(loginForm)));
        await refreshUser();
        if (!user) throw new ApiError(401);
        loginDialog.close();
        announce("已登录。");
        await render(true);
    } catch (error) {
        errorBox.textContent = error.status === 401 ? "用户名或密码错误。" : errorMessage(error);
        errorBox.hidden = false;
    } finally {
        byId("password").value = "";
        button.disabled = false;
        mutationPending = false;
    }
});

byId("logout").addEventListener("click", async event => {
    if (mutationPending) return;
    const button = event.currentTarget;
    mutationPending = true;
    button.disabled = true;
    try {
        await write("/api/auth/logout", "POST");
        user = null;
        authUi();
        announce("已退出登录。");
        await render(true);
    } catch (error) {
        handleExpiredSession(error);
        announce(errorMessage(error));
        if (!user) await render(true);
    } finally {
        button.disabled = false;
        mutationPending = false;
    }
});

function navigate(hash) {
    if (location.hash === hash) render();
    else location.hash = hash;
}

byId("search").addEventListener("submit", event => {
    event.preventDefault();
    navigate(listHash("works", 1, byId("keyword").value.trim(), tagSelect.value));
});
byId("search").addEventListener("reset", event => {
    event.preventDefault();
    navigate("#works");
});
window.addEventListener("hashchange", () => {
    // 跳转到内容链接只移动焦点。
    if (location.hash !== "#main-content") {
        announce();
        render(true);
    }
});

await refreshUser().catch(error => announce(errorMessage(error)));
loadTags().catch(() => {
    byId("tag-status").textContent = "标签暂时无法加载，仍可按标题搜索。";
    byId("tag-status").hidden = false;
});
await render();
