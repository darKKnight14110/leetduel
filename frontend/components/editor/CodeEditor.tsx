"use client";

import dynamic from "next/dynamic";
import type { BeforeMount } from "@monaco-editor/react";
import type { Language } from "@/lib/api";

const MonacoEditor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => <div className="h-[min(52vh,520px)] w-full animate-pulse rounded-xl border border-border bg-surface-2" />,
});

const editorLanguage: Record<Language, string> = {
  PYTHON: "python",
  JAVA: "java",
};

const defineTheme: BeforeMount = (monaco) => {
  monaco.editor.defineTheme("leetduel-dark", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#1f1f23",
      "editor.foreground": "#f5f5f4",
      "editorLineNumber.foreground": "#6f7078",
      "editorLineNumber.activeForeground": "#ffc53d",
      "editorCursor.foreground": "#ffc53d",
      "editor.selectionBackground": "#5b491a",
      "editor.inactiveSelectionBackground": "#3a321d",
    },
  });
};

export function CodeEditor({
  value,
  language,
  disabled = false,
  onChange,
}: {
  value: string;
  language: Language;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <div aria-label={`${language === "PYTHON" ? "Python" : "Java"} code editor`} className="overflow-hidden rounded-xl border border-border-strong">
      <MonacoEditor
        height="min(52vh, 520px)"
        language={editorLanguage[language]}
        theme="leetduel-dark"
        value={value}
        beforeMount={defineTheme}
        onChange={(nextValue) => onChange(nextValue ?? "")}
        options={{
          automaticLayout: true,
          ariaLabel: `${language === "PYTHON" ? "Python" : "Java"} code editor`,
          fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
          fontSize: 14,
          lineHeight: 22,
          minimap: { enabled: false },
          padding: { top: 16, bottom: 16 },
          readOnly: disabled,
          scrollBeyondLastLine: false,
          tabSize: 4,
          wordWrap: "on",
        }}
      />
    </div>
  );
}
