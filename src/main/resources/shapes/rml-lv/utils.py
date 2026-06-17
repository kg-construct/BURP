import os
import shutil

def replace_strings_in_file(file_path: str, replacements: dict) -> str:
    abs_path = os.path.abspath(file_path)
    tmp_dir = os.path.join(os.path.dirname(abs_path), 'tmp')
    os.makedirs(tmp_dir, exist_ok=True)
    tmp_file_path = os.path.join(tmp_dir, os.path.basename(abs_path))

    with open(abs_path, 'r') as file:
        file_data = file.read()

    for old_string, new_string in replacements.items():
        file_data = file_data.replace(old_string, new_string)

    with open(tmp_file_path, 'w') as file:
        file.write(file_data)

    return tmp_file_path

def cleanup_tmp_folder(file_path: str) -> None:
    abs_path = os.path.abspath(file_path)
    tmp_dir = os.path.dirname(abs_path)
    if os.path.exists(tmp_dir):
        shutil.rmtree(tmp_dir)
