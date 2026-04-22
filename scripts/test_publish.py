#!/usr/bin/env python3
import argparse
import subprocess
import os
import sys

def run_cmd(cmd, cwd=None):
    print(f"\n🚀 Running: {cmd}")
    is_windows = os.name == "nt"
    result = subprocess.run(cmd, shell=True, cwd=cwd, text=True)
    if result.returncode != 0:
        print(f"❌ Command failed with exit code {result.returncode}")
        sys.exit(result.returncode)
    print("✅ Success!")

def generate_sdk(lang):
    print(f"🛠️ Generating SDK for {lang.upper()}...")
    generator_map = {
        "java": "java",
        "python": "python",
        "flutter": "dart"
    }
    target_lang = generator_map.get(lang)
    if not target_lang:
        print(f"❌ Unsupported language for SDK: {lang}")
        sys.exit(1)
        
    cmd = f"npx @openapitools/openapi-generator-cli generate -i http://localhost:8080/v3/api-docs -g {target_lang} -o sdk/{lang} --skip-validate-spec"
    run_cmd(cmd)

def main():
    parser = argparse.ArgumentParser(description="Portfolio Management Orchestrator")
    parser.add_argument("action", nargs="?", help="Action to perform (run, build, deploy)")
    parser.add_argument("--sdk", choices=["java", "python", "flutter", "all"], help="Generate SDK for specific language")
    parser.add_argument("--env", choices=["preprod", "prod"], default="preprod", help="Target environment")
    
    args = parser.parse_args()
    
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    
    if args.sdk:
        if args.sdk == "all":
            for lang in ["java", "python", "flutter"]:
                generate_sdk(lang)
        else:
            generate_sdk(args.sdk)
        return

    if args.action == "run":
        run_cmd("mvn -pl portfolio-app spring-boot:run", cwd=repo_root)
    elif args.action == "build":
        run_cmd("mvn clean install -DskipTests", cwd=repo_root)
    elif args.action == "deploy":
        print(f"🚢 Deploying to {args.env}...")
        # Add helm deployment logic here if needed
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
