find . -name '*.class' | while read f; do javap -c -p -s -v "$f" | grep -q 'LList;' && echo "Found: $f"; done
