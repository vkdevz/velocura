import subprocess
import os

workspace_path = os.path.dirname(os.path.abspath(__file__))
backend_path = os.path.join(workspace_path, "velocura-backend")

print("Starting VeloCura Backend Server...")
os.chdir(backend_path)

subprocess.run([
    "./mvnw", 
    "spring-boot:run"
])

