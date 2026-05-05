(() => {
    const state = {
        activeChatId: document.body.dataset.activeChatId || null,
        streaming: false,
        autoScroll: true
    };

    const chatList = document.getElementById("chatList");
    const sidebar = document.getElementById("sidebar");
    const sidebarToggleBtn = document.getElementById("sidebarToggleBtn");
    const sidebarExpandBtn = document.getElementById("sidebarExpandBtn");
    const newChatBtn = document.getElementById("newChatBtn");
    const newChatCollapsedBtn = document.getElementById("newChatCollapsedBtn");
    const messagesEl = document.getElementById("messages");
    const form = document.getElementById("messageForm");
    const input = document.getElementById("messageInput");
    const sendBtn = document.getElementById("sendBtn");
    const typingIndicator = document.getElementById("typingIndicator");
    const COMPOSER_MIN_ROWS = 1;
    const COMPOSER_MAX_ROWS = 10;

    setupMarkdown();
    setupComposerInput();
    setupSidebar();
    bindSidebarActions();
    bindComposer();
    bindScrollBehavior();
    scrollToBottom();

    function bindSidebarActions() {
        newChatBtn?.addEventListener("click", async (event) => {
            event.stopPropagation();
            const created = await apiJson("/api/chats", {method: "POST"});
            window.location.href = `/chats/${created.id}`;
        });

        newChatCollapsedBtn?.addEventListener("click", async (event) => {
            event.stopPropagation();
            const created = await apiJson("/api/chats", {method: "POST"});
            window.location.href = `/chats/${created.id}`;
        });

        chatList?.addEventListener("click", async (event) => {
            const renameBtn = event.target.closest(".rename-chat-btn");
            if (renameBtn) {
                event.preventDefault();
                const item = renameBtn.closest(".chat-item");
                if (!item) return;
                const chatId = item.dataset.chatId;
                const currentTitle = item.querySelector(".chat-link")?.textContent?.trim() || "";
                const title = prompt("Новое имя чата:", currentTitle);
                if (!title) return;
                await apiJson(`/api/chats/${chatId}`, {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({title})
                });
                window.location.reload();
                return;
            }

            const deleteBtn = event.target.closest(".delete-chat-btn");
            if (deleteBtn) {
                event.preventDefault();
                const item = deleteBtn.closest(".chat-item");
                if (!item) return;
                const chatId = item.dataset.chatId;
                if (!confirm("Удалить чат?")) return;
                await fetch(`/api/chats/${chatId}`, {method: "DELETE"});
                window.location.href = "/";
                return;
            }

            const clickedLink = event.target.closest(".chat-link");
            if (clickedLink) {
                return;
            }

            const chatItem = event.target.closest(".chat-item");
            if (chatItem?.dataset.chatId) {
                window.location.href = `/chats/${chatItem.dataset.chatId}`;
            }
        });
    }

    function setupSidebar() {
        if (!sidebar) return;

        const stored = window.localStorage.getItem("sidebarCollapsed");
        if (stored === "1") {
            setSidebarCollapsed(true);
        }

        sidebarToggleBtn?.addEventListener("click", (event) => {
            event.stopPropagation();
            setSidebarCollapsed(!sidebar.classList.contains("collapsed"));
        });

        sidebarExpandBtn?.addEventListener("click", (event) => {
            event.stopPropagation();
            setSidebarCollapsed(false);
        });

        sidebar.addEventListener("click", (event) => {
            if (!sidebar.classList.contains("collapsed")) return;
            if (event.target.closest("#newChatBtn, #newChatCollapsedBtn, #sidebarExpandBtn")) return;
            setSidebarCollapsed(false);
        });
    }

    function bindComposer() {
        if (!form || !input) return;

        input.addEventListener("input", () => {
            adjustComposerHeight();
        });

        input.addEventListener("keydown", (event) => {
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                form.requestSubmit();
            }
        });

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!state.activeChatId || state.streaming) return;

            const content = input.value.trim();
            if (!content) return;

            state.streaming = true;
            state.autoScroll = true;
            setComposeDisabled(true);
            addMessage("USER", content);
            input.value = "";
            adjustComposerHeight();
            showTyping(true);

            const assistantNode = addMessage("ASSISTANT", "");

            try {
                await streamAssistant(content, assistantNode);
            } catch (error) {
                addError(error.message || "Ошибка при стриминге ответа.");
            } finally {
                state.streaming = false;
                setComposeDisabled(false);
                showTyping(false);
            }
        });
    }

    async function streamAssistant(content, assistantNode) {
        const response = await fetch(`/api/chats/${state.activeChatId}/messages/stream`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({content})
        });

        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({}));
            throw new Error(errorBody.error || "Не удалось получить ответ от сервера.");
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";
        let doneEventReceived = false;

        while (true) {
            const {value, done} = await reader.read();
            if (done) {
                break;
            }

            buffer += decoder.decode(value, {stream: true});
            const parts = buffer.split(/\r?\n\r?\n/);
            buffer = parts.pop() || "";

            for (const rawEvent of parts) {
                const parsed = parseSseEvent(rawEvent);
                if (!parsed) continue;

                const payload = parsePayload(parsed.data);
                const eventType = payload?.type || parsed.event;

                if (eventType === "token") {
                    appendAssistantChunk(assistantNode, payload.content || "");
                } else if (eventType === "done") {
                    doneEventReceived = true;
                } else if (eventType === "error") {
                    throw new Error(payload.error || "Ошибка генерации ответа.");
                }
            }
        }

        if (doneEventReceived) {
            await refreshActiveChatTitle();
        }
    }

    function parseSseEvent(chunk) {
        const lines = chunk.split(/\r?\n/);
        let eventName = "";
        const dataLines = [];
        for (const line of lines) {
            if (line.startsWith("event:")) {
                eventName = line.slice(6).trim();
            }
            if (line.startsWith("data:")) {
                dataLines.push(line.slice(5).trim());
            }
        }
        if (!eventName && dataLines.length === 0) {
            return null;
        }
        return {event: eventName, data: dataLines.join("\n")};
    }

    function parsePayload(data) {
        if (!data) return null;
        try {
            return JSON.parse(data);
        } catch (_) {
            return {type: "token", content: data};
        }
    }

    function appendAssistantChunk(messageNode, chunk) {
        const contentNode = messageNode.querySelector(".message-content");
        if (!contentNode) return;

        const current = contentNode.dataset.raw || "";
        const next = current + chunk;
        contentNode.dataset.raw = next;
        renderMarkdown(contentNode, next);

        if (state.autoScroll) {
            scrollToBottom();
        }
    }

    function addMessage(role, content) {
        const emptyState = messagesEl.querySelector(".empty-state");
        if (emptyState) emptyState.remove();

        const row = document.createElement("div");
        row.className = `message-row ${role === "USER" ? "user" : role === "SYSTEM" ? "system" : "assistant"}`;

        const card = document.createElement("div");
        card.className = "message-card";

        if (role !== "USER") {
            const meta = document.createElement("div");
            meta.className = "message-meta";
            meta.textContent = role;
            card.appendChild(meta);
        }

        const body = document.createElement("div");
        body.className = "message-content";
        if (role === "ASSISTANT") {
            body.classList.add("markdown");
            body.dataset.raw = content;
            renderMarkdown(body, content || "");
        } else {
            body.textContent = content;
        }
        card.appendChild(body);

        row.appendChild(card);
        typingIndicator.before(row);
        if (state.autoScroll) {
            scrollToBottom();
        }
        return row;
    }

    function setupMarkdown() {
        document.querySelectorAll(".message-content.markdown").forEach((node) => {
            const raw = node.textContent || "";
            node.dataset.raw = raw;
            renderMarkdown(node, raw);
        });
    }

    function renderMarkdown(element, raw) {
        const normalized = normalizeMarkdown(raw || "");
        element.innerHTML = marked.parse(normalized);
        beautifyCodeBlocks(element);
        highlightCodeBlocks(element);
    }

    function normalizeMarkdown(raw) {
        let text = String(raw).replace(/\r\n/g, "\n");

        // Convert one-line fenced code like:
        // ```bash liquibase rollback2 ```
        // into a valid multi-line fenced block.
        text = text.replace(/```([a-zA-Z0-9_+-]+)\s+([^\n`][^\n]*?)```/g, (_, lang, body) => {
            return `\`\`\`${lang}\n${body.trim()}\n\`\`\``;
        });

        // Fence marker should start on a new line (e.g. "Java```java").
        text = text.replace(/([^\n])```/g, "$1\n```");

        // Ensure line break after known language token in opening fence:
        // ```javapublic class X -> ```java\npublic class X
        const knownLanguages = [
            "javascript", "typescript", "python", "java", "kotlin", "scala", "groovy",
            "php", "ruby", "go", "rust", "swift", "csharp", "cs", "cpp", "c",
            "sql", "bash", "shell", "sh", "zsh", "fish", "console", "terminal", "powershell", "ps1", "json", "yaml",
            "yml", "xml", "html", "css", "scss", "markdown", "md", "dockerfile"
        ];
        const langPattern = knownLanguages
            .slice()
            .sort((a, b) => b.length - a.length)
            .map((lang) => lang.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
            .join("|");
        const openingFenceRegex = new RegExp("```(" + langPattern + ")([^\\s\\n`])", "gi");
        text = text.replace(openingFenceRegex, "```$1\n$2");

        // Headings sometimes come glued with bold start: "### Title**Text**"
        text = text.replace(/^(#{1,6}\s[^\n*]+)\*\*/gm, "$1\n**");
        // Fix malformed headings like "###8" -> "### 8"
        text = text.replace(/^(#{1,6})([^#\s].*)$/gm, "$1 $2");

        return text;
    }

    function beautifyCodeBlocks(root) {
        root.querySelectorAll("pre code").forEach((block) => {
            const source = block.textContent || "";
            const langClass = [...block.classList].find((cls) => cls.startsWith("language-")) || "";
            const lang = normalizeLanguageAlias(langClass.replace("language-", "").toLowerCase());

            const formatted = formatCodeForDisplay(source, lang);
            if (formatted && formatted !== source) {
                block.textContent = formatted;
            }
        });
    }

    function formatCodeForDisplay(source, lang) {
        const text = source.replace(/\r\n/g, "\n");
        if (!looksCollapsedCode(text)) {
            return text;
        }

        const cLikeLanguages = new Set([
            "java", "javascript", "typescript", "js", "ts", "php", "c", "cpp",
            "csharp", "cs", "go", "kotlin", "swift", "rust", "scala", "groovy"
        ]);

        if (cLikeLanguages.has(lang)) {
            return formatCLikeCode(text);
        }

        return text.replace(/\n{3,}/g, "\n\n");
    }

    function looksCollapsedCode(text) {
        const lines = text.split("\n");
        const nonEmpty = lines.filter((line) => line.trim().length > 0);
        const hasCodePunctuation = /[{};=()]/.test(text);
        const longLine = nonEmpty.some((line) => line.length > 120);
        const almostSingleLine = nonEmpty.length <= 2;

        return hasCodePunctuation && (almostSingleLine || longLine);
    }

    function formatCLikeCode(input) {
        const text = input.trim();
        let out = "";
        let indent = 0;
        let inSingle = false;
        let inDouble = false;
        let inTemplate = false;
        let inLineComment = false;
        let inBlockComment = false;
        let escape = false;
        let atLineStart = true;

        function writeIndent() {
            if (atLineStart) {
                out += "  ".repeat(Math.max(0, indent));
                atLineStart = false;
            }
        }

        function trimEndSpaces() {
            out = out.replace(/[ \t]+$/g, "");
        }

        for (let i = 0; i < text.length; i++) {
            const ch = text[i];
            const next = text[i + 1] || "";

            if (inLineComment) {
                writeIndent();
                out += ch;
                if (ch === "\n") {
                    inLineComment = false;
                    atLineStart = true;
                }
                continue;
            }

            if (inBlockComment) {
                writeIndent();
                out += ch;
                if (ch === "*" && next === "/") {
                    out += "/";
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inSingle || inDouble || inTemplate) {
                writeIndent();
                out += ch;
                if (escape) {
                    escape = false;
                    continue;
                }
                if (ch === "\\") {
                    escape = true;
                    continue;
                }
                if (inSingle && ch === "'") inSingle = false;
                if (inDouble && ch === "\"") inDouble = false;
                if (inTemplate && ch === "`") inTemplate = false;
                continue;
            }

            if (ch === "/" && next === "/") {
                writeIndent();
                out += "//";
                i++;
                inLineComment = true;
                continue;
            }

            if (ch === "/" && next === "*") {
                writeIndent();
                out += "/*";
                i++;
                inBlockComment = true;
                continue;
            }

            if (ch === "'") {
                writeIndent();
                out += ch;
                inSingle = true;
                continue;
            }

            if (ch === "\"") {
                writeIndent();
                out += ch;
                inDouble = true;
                continue;
            }

            if (ch === "`") {
                writeIndent();
                out += ch;
                inTemplate = true;
                continue;
            }

            if (ch === "{") {
                trimEndSpaces();
                writeIndent();
                out += "{\n";
                indent++;
                atLineStart = true;
                continue;
            }

            if (ch === "}") {
                trimEndSpaces();
                if (!atLineStart) {
                    out += "\n";
                }
                indent = Math.max(0, indent - 1);
                out += "  ".repeat(indent) + "}";
                atLineStart = false;
                const nextNonSpace = text.slice(i + 1).match(/\S/)?.[0] || "";
                if (nextNonSpace && nextNonSpace !== ";" && nextNonSpace !== "}" && nextNonSpace !== ",") {
                    out += "\n";
                    atLineStart = true;
                }
                continue;
            }

            if (ch === ";") {
                writeIndent();
                out += ";\n";
                atLineStart = true;
                continue;
            }

            if (ch === "\n") {
                trimEndSpaces();
                if (!out.endsWith("\n")) {
                    out += "\n";
                }
                atLineStart = true;
                continue;
            }

            if (atLineStart && /\s/.test(ch)) {
                continue;
            }

            writeIndent();
            out += ch;
        }

        return out
            .replace(/[ \t]+\n/g, "\n")
            .replace(/\n{3,}/g, "\n\n")
            .trim();
    }

    function highlightCodeBlocks(root) {
        if (!window.hljs) return;
        root.querySelectorAll("pre code").forEach((block) => {
            const langClass = [...block.classList].find((cls) => cls.startsWith("language-")) || "";
            const lang = normalizeLanguageAlias(langClass.replace("language-", "").toLowerCase());
            if (lang && lang !== langClass.replace("language-", "").toLowerCase()) {
                if (langClass) block.classList.remove(langClass);
                block.classList.add(`language-${lang}`);
            }

            try {
                window.hljs.highlightElement(block);
            } catch (_) {
                // keep unhighlighted code if language detection failed
            }
        });
    }

    function normalizeLanguageAlias(lang) {
        if (!lang) return "";
        const map = {
            sh: "bash",
            shell: "bash",
            zsh: "bash",
            fish: "bash",
            console: "bash",
            terminal: "bash",
            ps1: "powershell"
        };
        return map[lang] || lang;
    }

    function bindScrollBehavior() {
        if (!messagesEl) return;
        messagesEl.addEventListener("scroll", () => {
            state.autoScroll = isNearBottom();
        });
    }

    function showTyping(show) {
        typingIndicator.classList.toggle("d-none", !show);
        if (show && state.autoScroll) scrollToBottom();
    }

    function setComposeDisabled(disabled) {
        input.disabled = disabled;
        sendBtn.disabled = disabled;
    }

    function setSidebarCollapsed(collapsed) {
        if (!sidebar) return;
        sidebar.classList.toggle("collapsed", collapsed);
        window.localStorage.setItem("sidebarCollapsed", collapsed ? "1" : "0");
    }

    function setupComposerInput() {
        if (!input) return;
        adjustComposerHeight();
    }

    function adjustComposerHeight() {
        if (!input) return;

        const style = window.getComputedStyle(input);
        const lineHeight = parseFloat(style.lineHeight) || 24;
        const minHeight = lineHeight * COMPOSER_MIN_ROWS;
        const maxHeight = lineHeight * COMPOSER_MAX_ROWS;

        input.style.height = "auto";
        const nextHeight = Math.min(Math.max(input.scrollHeight, minHeight), maxHeight);
        input.style.height = `${nextHeight}px`;
        input.style.overflowY = input.scrollHeight > maxHeight ? "auto" : "hidden";
    }

    function addError(message) {
        const row = addMessage("SYSTEM", `Ошибка: ${message}`);
        row.querySelector(".message-content").classList.remove("markdown");
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function isNearBottom() {
        const threshold = 80;
        const distance = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight;
        return distance <= threshold;
    }

    async function refreshActiveChatTitle() {
        if (!state.activeChatId || !chatList) return;

        try {
            const chat = await apiJson(`/api/chats/${state.activeChatId}`);
            if (!chat?.title) return;

            const activeItem = chatList.querySelector(`.chat-item[data-chat-id="${state.activeChatId}"]`);
            const titleNode = activeItem?.querySelector(".chat-link");
            if (titleNode) {
                titleNode.textContent = chat.title;
            }
        } catch (_) {
            // Keep chat functional even if title refresh failed.
        }
    }

    async function apiJson(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.error || "Ошибка API.");
        }
        return response.json();
    }
})();
