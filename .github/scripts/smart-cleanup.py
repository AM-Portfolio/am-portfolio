import os
import requests
from datetime import datetime

# Environment Variables
GITHUB_TOKEN = os.getenv('GITHUB_TOKEN')
ORG_NAME = "AM-Portfolio"
PACKAGE_NAME = "am-portfolio"
REPO_NAME = "am-portfolio"
MAIN_KEEP = 5
FEATURE_KEEP = 5

HEADERS = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "X-GitHub-Api-Version": "2022-11-28"
}

def get_package_versions():
    url = f"https://api.github.com/orgs/{ORG_NAME}/packages/container/{PACKAGE_NAME}/versions"
    response = requests.get(url, headers=HEADERS, params={"per_page": 100})
    response.raise_for_status()
    return response.json()

def get_run_branch(run_id):
    url = f"https://api.github.com/repos/{ORG_NAME}/{REPO_NAME}/actions/runs/{run_id}"
    response = requests.get(url, headers=HEADERS)
    if response.status_code == 200:
        return response.json().get('head_branch')
    return None

def delete_version(version_id):
    url = f"https://api.github.com/orgs/{ORG_NAME}/packages/container/{PACKAGE_NAME}/versions/{version_id}"
    response = requests.delete(url, headers=HEADERS)
    if response.status_code == 204:
        print(f"Successfully deleted version {version_id}")
    else:
        print(f"Failed to delete {version_id}: {response.status_code} - {response.text}")

def main():
    if not GITHUB_TOKEN:
        print("Missing GITHUB_TOKEN!")
        exit(1)

    print(f"Fetching package versions for {PACKAGE_NAME}...")
    versions = get_package_versions()
    
    # Sort by created_at descending
    versions.sort(key=lambda x: x['created_at'], reverse=True)
    
    main_versions = []
    feature_versions = []
    other_versions = []
    
    print(f"Total versions found: {len(versions)}")

    for v in versions:
        version_id = v['id']
        tags = v['metadata']['container']['tags']
        
        # Skip if 'latest' is in tags (don't delete latest)
        if 'latest' in tags:
            print(f"Keeping version {version_id} (tagged as latest)")
            continue
            
        # Try to find a numeric run_id tag
        run_tag = None
        for tag in tags:
            if tag.isdigit():
                run_tag = tag
                break
                
        if run_tag:
            branch = get_run_branch(run_tag)
            if branch == 'main' or branch == 'master':
                main_versions.append(v)
            else:
                feature_versions.append(v)
        else:
            # If there's no numeric tag, categorize as other
            other_versions.append(v)
            
    print(f"Found {len(main_versions)} main builds, {len(feature_versions)} feature builds.")
    
    # Identify which to delete
    to_delete = []
    
    if len(main_versions) > MAIN_KEEP:
        to_delete.extend(main_versions[MAIN_KEEP:])
    if len(feature_versions) > FEATURE_KEEP:
        to_delete.extend(feature_versions[FEATURE_KEEP:])
        
    to_delete.extend(other_versions) # Optionally delete untagged/other entirely, but let's just delete the ones exceeding limit
    
    if not to_delete:
        print("Nothing to clean up! Exiting.")
        return

    print(f"Proceeding to delete {len(to_delete)} old versions...")
    for v in to_delete:
        print(f"Deleting version {v['id']} created at {v['created_at']}")
        delete_version(v['id'])

if __name__ == "__main__":
    main()
