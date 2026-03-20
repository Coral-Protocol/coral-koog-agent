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
const readline = require('readline');

function run(command, options = {}) {
    try {
        execSync(command, { stdio: 'inherit', ...options });
    } catch (error) {
        process.exit(1);
    }
}

const ask = (query) => {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });
    return new Promise((resolve) => rl.question(query, (ans) => {
        rl.close();
        resolve(ans);
    }));
};

async function main() {
    let targetDir = process.argv[2];

    if (!targetDir) {
        console.log('\nLet\'s set up your new koog agent. We\'ll add give it CoralOS support out of the box.');
        targetDir = await ask('What is your project name? (e.g., my-cool-agent): ');
        if (!targetDir) {
            console.error('\n❌ Project name is required.');
            process.exit(1);
        }
    }

    const fullPath = path.resolve(process.cwd(), targetDir);

    if (fs.existsSync(fullPath)) {
        const files = fs.readdirSync(fullPath).filter(f => !f.startsWith('.'));
        if (files.length > 0) {
            console.log(`\n⚠️  Warning: Directory '${targetDir}' is not empty.`);
            const confirm = await ask('Do you want to continue anyway? (y/N): ');
            if (confirm.toLowerCase() !== 'y') {
                process.exit(0);
            }
        }
    } else {
        fs.mkdirSync(fullPath, { recursive: true });
    }

    process.chdir(fullPath);

    const TEMPLATE_REPO = 'https://github.com/Coral-Protocol/coral-koog-agent.git';

    console.log(`\n📦 Cloning template repository from ${TEMPLATE_REPO}...`);
    run(`git clone ${TEMPLATE_REPO} .`);

    console.log('\n🚀 Running hydration task...');
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

    // Extract potential hydrate arguments from CLI
    const hydrateArgs = process.argv.slice(3).join(' ');
    
    // Automatically pass agentName if it's not already provided in args
    let autoArgs = '';
    if (!hydrateArgs.includes('--agentName')) {
        const agentName = path.basename(fullPath);
        // Only pass if it looks like a valid kebab-case name to avoid immediate validation failure
        if (agentName.match(/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/)) {
            autoArgs += `--agentName=${agentName} `;
        }
    }

    // Use --console=plain and -q to keep the output clean during interactive hydration
    run(`${gradlew} hydrate --console=plain -q ${autoArgs} ${hydrateArgs}`);

    console.log('\n✅ Project created and hydrated successfully!');
    console.log(`\nNext steps:
  cd ${targetDir}
  ./gradlew run
`);
}

main().catch(err => {
    console.error(`\n❌ Error: ${err.message}`);
    process.exit(1);
});
