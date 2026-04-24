import os
path = "additions/build/libs"
if os.path.exists(path):
    print(os.listdir(path))
else:
    print("Path not found")
