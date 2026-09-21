// TypeScript 6's checker requires an ambient module declaration even for plain
// side-effect CSS imports (e.g. `import "./globals.css"` in layout.tsx) — Next.js's
// own next-env.d.ts only declares `*.module.css`, not plain `*.css`.
declare module "*.css";
