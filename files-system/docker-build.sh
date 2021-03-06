#!/usr/bin/env sh

FS_FOLDER=files-system
VERSION="${CI_COMMIT_TAG:-${CI_COMMIT_REF_SLUG:-$(date +"%s")}}"

./${FS_FOLDER}/compile.sh

cd ${FS_FOLDER}/target/docker/ || exit 1
docker build -t podcastserver/file-system:"${VERSION}" .
[ -n "$CI" ] && docker push "podcastserver/file-system:${VERSION}"

if [ "$VERSION" == "master" ]; then
  docker tag "podcastserver/file-system:${VERSION}" podcastserver/file-system:latest
  [ -n "$CI" ] && docker push podcastserver/file-system:latest
fi
