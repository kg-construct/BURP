import typescriptEslint from "typescript-eslint";
import eslintConfigPrettier from "eslint-config-prettier/flat";
import {defineConfig, globalIgnores} from "eslint/config";

export default defineConfig([
    {files: ["**/*.ts"]},
    globalIgnores(["src/generated/types.ts"]),
    {
        plugins: {
            "@typescript-eslint": typescriptEslint.plugin,
        },

        languageOptions: {
            parser: typescriptEslint.parser,
            ecmaVersion: 2022,
            sourceType: "module",
        },

        rules: {
            "@typescript-eslint/naming-convention": ["warn", {
                selector: "import",
                format: ["camelCase", "PascalCase"],
            }],

            curly: "warn",
            eqeqeq: "warn",
            "no-throw-literal": "warn",
            semi: "warn",
        },
    },
    eslintConfigPrettier,
]);