import subprocess
import os

print("Starting Vite Frontend Server...")
os.chdir("velocura-frontend")
subprocess.run(["npm", "run", "dev"])
