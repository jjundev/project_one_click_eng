
import os
import shutil
from PIL import Image

# Paths
res_dir = r"e:\AndroidLab\project_one_click_eng\android\app\src\main\res"
artifact_dir = r"C:\Users\Hyunjun\.gemini\antigravity\brain\75920419-4218-46db-b654-eaf5e8c357a6"
fg_img_path = os.path.join(artifact_dir, "app_logo_foreground_png_1771759280675.png") # Update this with the actual name
bg_img_path = os.path.join(artifact_dir, "app_logo_full_png_1771759312628.png")

# Mipmap configurations (size, folder)
mipmap_configs = [
    (48, "mipmap-mdpi"),
    (72, "mipmap-hdpi"),
    (96, "mipmap-xhdpi"),
    (144, "mipmap-xxhdpi"),
    (192, "mipmap-xxxhdpi")
]

def resize_and_save(img_path, target_path, size):
    with Image.open(img_path) as img:
        img_resized = img.resize((size, size), Image.Resampling.LANCZOS)
        img_resized.save(target_path, "WEBP")

# 1. Update mipmap images
for size, folder in mipmap_configs:
    target_folder = os.path.join(res_dir, folder)
    if not os.path.exists(target_folder):
        os.makedirs(target_folder)
    
    # Save as ic_launcher.webp and ic_launcher_round.webp
    output_path = os.path.join(target_folder, "ic_launcher.webp")
    resize_and_save(bg_img_path, output_path, size)
    
    output_path_round = os.path.join(target_folder, "ic_launcher_round.webp")
    resize_and_save(bg_img_path, output_path_round, size)

# 2. Update Adaptive Icon Foreground (optional if we want to replace the XML with PNG, 
# but usually it's better to keep XML for vector or replace the PNG it refers to)
# Let's save a high-res version to drawable as well
drawable_dir = os.path.join(res_dir, "drawable")
if not os.path.exists(drawable_dir):
    os.makedirs(drawable_dir)

# Save foreground PNG to drawable for adaptive icon reference if needed
shutil.copy(fg_img_path, os.path.join(drawable_dir, "ic_launcher_foreground_new.png"))

print("Successfully updated mipmap resources.")
