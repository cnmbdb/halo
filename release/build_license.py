"""Compile the source-owned license extension before repackaging the upstream JAR."""
import os
import shutil
import subprocess


def build_license(root):
    maven = os.environ.get('MVN') or shutil.which('mvn')
    if not maven:
        raise RuntimeError('JDK 21 and Maven 3.9+ are required. Set JAVA_HOME and MVN, then rebuild.')
    subprocess.run([maven, '-q', '-f', str(root / 'release/license-backend/pom.xml'), 'verify'], check=True)
