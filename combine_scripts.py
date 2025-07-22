import os

# Root directory from which the search begins
root_directory = '/Users/dannysaleeb/Library/Application Support/SuperCollider/Extensions/VoxLib'

# List of directory names you want to scrape text files from
target_dir_names = ['Classes', 'Modules', 'Extensions', 'Utils']

# Output file to store combined contents
output_file = 'combined_text.txt'

with open(output_file, 'w', encoding='utf-8') as outfile:
    for dirpath, dirnames, filenames in os.walk(root_directory):
        # Check if the current directory matches any target directory names
        if os.path.basename(dirpath) in target_dir_names:
            print(os.path.basename(dirpath))
            for filename in filenames:
                if filename.endswith('.sc'):
                    file_path = os.path.join(dirpath, filename)
                    with open(file_path, 'r', encoding='utf-8') as infile:
                        contents = infile.read()
                        outfile.write(contents + '\n')  # newline separator
                        
print(f"All targeted files combined into '{output_file}'.")
