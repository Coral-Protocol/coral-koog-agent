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

    // Check if the first argument is actually an option instead of a target directory
    if (targetDir && targetDir.startsWith('--')) {
        targetDir = undefined;
    }

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

    // Load version from package.json and attempt to checkout the corresponding tag
    try {
        const pkg = require(path.join(__dirname, 'package.json'));
        const version = pkg.version;
        if (version) {
            const tagsToTry = [version, `v${version}`];
            let checkedOut = false;
            for (const tag of tagsToTry) {
                try {
                    // Try to checkout the tag silently
                    execSync(`git checkout -q tags/${tag}`, { stdio: 'ignore' });
                    console.log(`\n📌 Using template version ${tag}`);
                    checkedOut = true;
                    break;
                } catch (e) {
                    // Tag not found, try the next one
                }
            }
            if (!checkedOut && !version.includes('SNAPSHOT')) {
                console.log(`\n⚠️  Note: No git tag found for version ${version}, using latest from default branch.`);
            }
        }
    } catch (err) {
        // Silently continue if package.json cannot be read or version is missing
    }

    console.log('\n🚀 Running hydration task...');
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

    // Extract potential hydrate arguments from CLI
    // We want to pass everything after the script name that starts with -- OR follows an option starting with --
    const hydrateArgs = [];
    for (let i = 2; i < process.argv.length; i++) {
        const arg = process.argv[i];
        if (arg === targetDir && i === 2) continue; // Skip the project name if it was at index 2

        if (arg.startsWith('--')) {
            hydrateArgs.push(arg);
            // If the next argument doesn't start with --, it might be the value for this option
            if (i + 1 < process.argv.length && !process.argv[i + 1].startsWith('--')) {
                hydrateArgs.push(process.argv[i + 1]);
                i++;
            }
        }
    }

    const hydrateArgsStr = hydrateArgs.join(' ');
    
    // Automatically pass agentName if it's not already provided in args
    let autoArgs = '';
    if (!hydrateArgsStr.includes('--agentName')) {
        const agentName = path.basename(fullPath);
        // Only pass if it looks like a valid kebab-case name to avoid immediate validation failure
        if (agentName.match(/^[a-z][a-z0-9]*(-[a-z0-9]+)*$/)) {
            autoArgs += `--agentName=${agentName} `;
        }
    }

    // Use --console=plain and -q to keep the output clean during interactive hydration
    run(`${gradlew} -q hydrate --console=plain ${autoArgs} ${hydrateArgsStr}`);

    console.log('\n✅ Project created and hydrated successfully!');
    console.log(`\nNext steps:
  cd ${targetDir}
  ./gradlew -q run
`);
}

main().catch(err => {
    console.error(`\n❌ Error: ${err.message}`);
    process.exit(1);
});
