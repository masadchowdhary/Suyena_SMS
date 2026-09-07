# Suyena Bulk SMS App

An Android application for bulk SMS delivery with MMS conversion protection, comma-separated text file import, and automatic group/batch splitting.

## Key Features
- **MMS Prevention (4–10 SMS Limit Size)**: Enforces SMS part limits to prevent long messages from being automatically converted into MMS by mobile carriers.
- **Comma-Separated Text File Import**: Import `.txt` files containing comma-separated or newline-separated phone numbers.
- **Group/Batch Splitting**: Enter a custom group size (e.g. 120) to automatically chunk large contact lists (e.g., 300 numbers split into 120 + 120 + 60).
- **Automated GitHub Releases**: CI/CD workflow included to build and upload release APKs automatically whenever a version tag is pushed.

## How to Create an Automatic GitHub Release

To release a new version of the app on GitHub:

1. Update version name in `app/build.gradle.kts` (e.g., `versionName = "1.0.0"`).
2. Commit your changes:
   ```bash
   git add .
   git commit -m "Release v1.0.0"
   ```
3. Tag the commit with your version:
   ```bash
   git tag v1.0.0
   ```
4. Push your commits and tags to GitHub:
   ```bash
   git push origin main --tags
   ```

GitHub Actions will automatically run the build and publish the `.apk` files under the **Releases** section on your GitHub repository!
