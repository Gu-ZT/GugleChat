<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const props = defineProps<{ content: string }>()

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function sanitize(html: string): string {
  // Strip dangerous tags and event handlers
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object[\s\S]*?<\/object>/gi, '')
    .replace(/<embed[\s\S]*?>/gi, '')
    .replace(/on\w+\s*=\s*["'][^"']*["']/gi, '')
    .replace(/on\w+\s*=\s*[^\s>]+/gi, '')
    .replace(/javascript\s*:/gi, '')
}

const md: MarkdownIt = new MarkdownIt({
  html: false, linkify: true, typographer: true, breaks: true,
  highlight(str: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try { return `<pre class="hljs"><code>${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>` } catch {}
    }
    return `<pre class="hljs"><code>${escapeHtml(str)}</code></pre>`
  },
})

// Custom renderer: video/audio links → embedded players
const defaultLinkRender = md.renderer.rules.link_open || function (tokens, idx, options, _env, self) {
  return self.renderToken(tokens, idx, options)
}
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const href = tokens[idx].attrGet('href') || ''
  const ext = href.split('.').pop()?.toLowerCase() || ''
  if (['mp4', 'webm', 'mov'].includes(ext)) {
    return `<video controls style="max-width:100%;border-radius:6px;max-height:360px" src="${href}">`
  }
  if (['mp3', 'wav', 'ogg', 'flac', 'm4a', 'aac', 'opus', 'weba'].includes(ext)) {
    return `<audio controls style="width:100%" src="${href}">`
  }
  return defaultLinkRender(tokens, idx, options, env, self)
}

const rendered = computed(() => sanitize(md.render(props.content)))
</script>

<template>
  <div class="markdown-body" v-html="rendered" />
</template>

<style>
.markdown-body { line-height: 1.6; }
.markdown-body p { margin: 0 0 8px; }
.markdown-body p:last-child { margin-bottom: 0; }
.markdown-body code { background: rgba(255,255,255,.1); padding: 2px 6px; border-radius: 3px; font-size: 13px; font-family: 'Fira Code', monospace; }
.markdown-body pre { background: #0d1117; padding: 12px 16px; border-radius: 6px; overflow-x: auto; margin: 8px 0; }
.markdown-body pre code { background: none; padding: 0; font-size: 13px; }
.markdown-body a { color: #80b4ff; text-decoration: none; }
.markdown-body a:hover { text-decoration: underline; }
.markdown-body blockquote { border-left: 3px solid #555; padding-left: 12px; color: #aaa; margin: 8px 0; }
.markdown-body img { max-width: 100%; border-radius: 6px; }
.markdown-body ul, .markdown-body ol { padding-left: 20px; margin: 4px 0; }
.markdown-body table { border-collapse: collapse; width: 100%; margin: 8px 0; }
.markdown-body th, .markdown-body td { border: 1px solid #444; padding: 6px 12px; text-align: left; }
.markdown-body th { background: rgba(255,255,255,.05); }
.hljs { color: #c9d1d9; background: #0d1117; }
.hljs-keyword { color: #ff7b72; }
.hljs-string { color: #a5d6ff; }
.hljs-number { color: #79c0ff; }
.hljs-comment { color: #8b949e; }
.hljs-function, .hljs-title { color: #d2a8ff; }
</style>
