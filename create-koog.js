#!/usr/bin/env node

/**
 * create-koog
 * 
 * This script initializes a new Coral Koog agent project by cloning the template
 * and running the hydration task.
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

function run(command, options = {}) {
    try {
        execSync(command, { stdio: 'inherit', ...options });
    } catch (error) {
        console.error(`\n❌ Failed to execute: ${command}`);
        process.exit(1);
    }
}

const targetDir = process.argv[2] || '.';
const fullPath = path.resolve(process.cwd(), targetDir);

console.log(`\nInitializing Coral Koog agent in: ${fullPath}`);

if (targetDir !== '.' && !fs.existsSync(fullPath)) {
    fs.mkdirSync(fullPath, { recursive: true });
}

process.chdir(fullPath);

// Check if directory is empty (ignoring dotfiles)
const files = fs.readdirSync('.').filter(f => !f.startsWith('.'));
if (files.length > 0) {
    console.error('\n❌ Directory is not empty. Please run in an empty directory or specify a new one.');
    process.exit(1);
}

const TEMPLATE_REPO = 'https://github.com/Coral-Protocol/coral-koog-agent.git';

console.log(`\n📦 Cloning template repository from ${TEMPLATE_REPO}...`);
run(`git clone ${TEMPLATE_REPO} .`);

console.log('\n🚀 Running hydration task...');
const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

// Allow users to pass arguments to the hydrate task, e.g., --agentName=...
const hydrateArgs = process.argv.slice(3).join(' ');
run(`${gradlew} hydrate ${hydrateArgs}`);

console.log('\n✅ Project created and hydrated successfully!');
console.log(`\nNext steps:
  cd ${targetDir}
  ./gradlew run
`);
