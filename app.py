import os
import subprocess
import shutil
import zipfile
from flask import Flask, request, send_file, jsonify

app = Flask(__name__)
BUILD_DIR = "/tmp/android_build"

@app.route('/build', methods=['POST'])
def build_apk():
    try:
        if 'file' not in request.files:
            return jsonify({"error": "No file uploaded"}), 400

        # Clean workspace
        if os.path.exists(BUILD_DIR):
            shutil.rmtree(BUILD_DIR)
        os.makedirs(BUILD_DIR)

        # Save and extract project
        zip_path = os.path.join(BUILD_DIR, "project.zip")
        request.files['file'].save(zip_path)
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(BUILD_DIR)

        # Make gradlew executable
        gradlew = os.path.join(BUILD_DIR, "gradlew")
        if os.path.exists(gradlew):
            os.chmod(gradlew, 0o755)

        # BUILD THE APK
        process = subprocess.run(
            ["./gradlew", "assembleDebug"], 
            cwd=BUILD_DIR, 
            capture_output=True, 
            text=True
        )

        if process.returncode != 0:
            return jsonify({"error": "Build failed", "details": process.stderr}), 500

        # Find the APK (standard path)
        apk_path = os.path.join(BUILD_DIR, "app/build/outputs/apk/debug/app-debug.apk")
        return send_file(apk_path, as_attachment=True)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=10000)
