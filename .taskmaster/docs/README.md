# PRD Location

Put the product requirements document for this project in this directory, for example:

- `.taskmaster/docs/prd.txt`
- `.taskmaster/docs/prd.md`

Then parse it from the project root with:

```bash
npm run tm -- parse-prd .taskmaster/docs/prd.txt --num-tasks 10 --force
```

If your PRD is Markdown, replace the filename with `.taskmaster/docs/prd.md`.
