#!/bin/bash

set -e

mkdir -p ./build
rm -f ./build/*.jar

cd ./versions || { echo "Missing ./versions directory"; exit 1; }

for versionDir in */; do
    versionDir=${versionDir%/}
    jarDir="$versionDir/build/libs"

    if [ -d "$jarDir" ]; then
        # Find "chatclef-VERSION-MODVERSION.jar" (no -sources or -all)
        jars=()
        while IFS= read -r -d '' jar; do
            filename=$(basename "$jar")
            if [[ "$filename" =~ ^chatclef-$versionDir-[0-9]+\.[0-9]+\.[0-9]+\.jar$ ]]; then
                jars+=("$jar")
            fi
        done < <(find "$jarDir" -maxdepth 1 -type f -name "chatclef-$versionDir-*.jar" -print0)

        if [ ${#jars[@]} -gt 0 ]; then
            sorted=($(printf "%s\n" "${jars[@]}" | sort -t '-' -k3 -V))
            latestJar="${sorted[-1]}"
            echo "Copying: $(basename "$latestJar")"
            cp "$latestJar" ../build/
        else
            echo "No valid JARs found in $versionDir"
        fi
    else
        echo "No build/libs directory in $versionDir"
    fi
done

