# Task Master Usage

This project is prepared for Task Master 0.43.1.

## China npm mirror

The project has a local `.npmrc` that uses the npmmirror registry and longer network timeouts. This only affects commands run inside this project.

## Windows PowerShell / CMD

Because `cmd.exe` does not support UNC paths as the current directory, use `pushd` so Windows maps the WSL path to a temporary drive letter:

```cmd
pushd "\\wsl.localhost\Ubuntu\home\oslab\projects\mini-mall-order"
npm install
npm run tm -- --version
popd
```

## Parse a PRD

Put the PRD at `.taskmaster/docs/prd.txt`, then run:

```cmd
pushd "\\wsl.localhost\Ubuntu\home\oslab\projects\mini-mall-order"
npm run parse-prd
popd
```

Or specify another PRD path:

```cmd
pushd "\\wsl.localhost\Ubuntu\home\oslab\projects\mini-mall-order"
npm run tm -- parse-prd .taskmaster/docs/prd.md --num-tasks 10 --force
popd
```

Generated tasks are stored in `.taskmaster/tasks/tasks.json`.

## WSL shell

WSL currently needs Node.js installed before it can run Task Master directly:

```bash
cd /home/oslab/projects/mini-mall-order
npm install
npm run parse-prd
```