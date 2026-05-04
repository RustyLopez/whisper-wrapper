FROM python:3.10-trixie
# TOOD: NOTE we probably want alpine as the base.
# but i put a lot of effort into getting the license extraction for ffmpeg below as intended using apt which is not applicable to alpine.
# and then switched this from another base to alpine and we'd have to change all of that, as well as the THIRD-PARTY-LICENSES possibly
# so for now making usre we are using apt-get
#    NOTE that it looks like the alpine package for ffmpeg does have the GPL license https://pkgs.alpinelinux.org/package/edge/community/x86/ffmpeg
#    so if using alpine we'd have to build our own ffmpeg package, though i'm not 100% sure whisper can work with the reduced feature set of the
#    less license heavy version.
# FROM python:3.10-alpine


# NOTE at this time we actualy wan tthis on the CPU so that the ollama or vllm image can run on the GPU
# BUT if we end up needing more juic efor reail time translation then
# https://docs.nvidia.com/cuda/cuda-installation-guide-linux/#wsl-installation-common
# apt update
# apt install cuda-toolkit
#
# possibly needs post install
# https://docs.nvidia.com/cuda/cuda-installation-guide-linux/#post-installation-actions
#
# And some systems may need to install an older version of cuda.
# Anyway
# for now we'll stick to cpu which will help keep image size way down, while at the same time, allowing us to use the
# GPU for the LLm.

# TODO IMPORTANT: since we are including ffmpeg in our image, rather than using a base that has already bundled it,
#  we'll need to include their license when building the docker image, or, find a base that already has ffmpeg
#  installed or find a way for the user to provide their own ffmpeg OR, just not deploy a docker hub hosted distribution
#  of this software. But this has to be addressed before any such artifact is bundled and deployed.
#  Please do not remove this "TODO" until this is resolved. And please do not deploy a built and bundled version of tihs
#  image until this is resolved.
#
# EDIT: Recommended install procedure for ensureing licenses and notices included with the ffmpeg pacakge are retained
#
# NOTE an additional recommend that we probably would want to follow.  Not sure we have the option of basing off the
# existing jrottenberg/ffmpeg image. But we may be able to build ffmpeg ourselves to ensure we are only obligated by lgbl
#
# NOTE: we may also be able to simply require the user to mount a volume with an ffmpeg binary or proxy, and then we
# have a configurable, but defaulted, param for where to find ffmpeg within that mounted volume. That way we can avoid
# the licensing obligations altogether.
#
# NOTE that for now we have added what should be, given the current, open source and free use case, a compliant license
# and copyright notice statement to THIRD-PARTY-LICENSES. But if we move to deploy a version of this application that
# pulls comercial revenue, then we may need to work harde to ensure we are not bundling any incompatible licenses.
#
#
# Using a pre-built minimalist Docker image that explicitly states its license (like jrottenberg/ffmpeg).
# Building FFmpeg from source yourself in the Dockerfile with
# --enable-lgpl (and avoiding --enable-gpl and non-free options) and including the source or a clear build manifest.
#
# EDIT OKAY if using trixie, we don't need to install ffmpeg. It's already been bundled and licensed. So that saves a lot
# of licensing heartache at the cost of a much larger image. EDIT: Nvm the version that ships is not sufficient
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && \
    apt-get clean && rm -rf /var/lib/apt/lists/* && \
    mkdir -p /usr/share/licenses/ffmpeg && \
    cp -r /usr/share/doc/ffmpeg/copyright /usr/share/licenses/ffmpeg/ || true
RUN apt show ffmpeg
# TOD: the above to attempt to see how this particular bundle  of ffmpeg is licensed
# Recommended for our Docker hub file if/when we get to that point
# This image includes the FFmpeg package (via apt). It is licensed under LGPL 2.1 (with possible GPL components).
# Full license and copyright details are inside the image in /usr/share/doc/ffmpeg/copyright
# and /usr/share/common-licenses/.



# ================================ \
#    NOTE IMPORTANT: This "whisperx" is a "BSD 2-Clause License" Licensed package.
#
# Therefore this docker image cannot be published in a packaged form without including the appropriate copyright notice,
# which must therefore be included in the docker image.
# copyright notice and license is found in THIRD-PARTY-LICENSES


RUN pip install whisperx

# TODO: MORE licensing woes. This may be a licensing issue.  If our base image included the correto JDK, then we could
# wouldn't need another 3rd party license. However, if we go this route, of basing on pythong and installing java, then
# we may have to deal with licenses and the licenses may be restrictive.
# I need to look at the python sdk / runtime and what its license is. It may be that we want to base off of an image that
# will handle the distribution of the more restrictive components. And then we only distribute our layer with the less
# restrictive licenses.
#
# of note this GPL may be a real issue if we want to make a comercial product.
# or even if we want our license to be less strict than that GPL
#
# "Linking this library statically or dynamically with other modules is making
#    a combined work based on this library.  Thus, the terms and conditions of
#    the GNU General Public License cover the whole combination."
#
# THOUGH I'd assume using corretto at all would carry this licensing restriction, and naturally our java app will.
#  BUT that is probably covered by the class path exemption.
#  NOTE that the docker image is not "linking" to the library and the java code has the class path exception.
#  So we may be okay but I need to do some digging. Again not a super critical issue until we go to delpoy this to
#  docker hub.
#
#
# add java, we need both
# https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/amazon-linux-install.html
RUN wget -O - https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg
RUN echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" | tee /etc/apt/sources.list.d/corretto.list
RUN apt-get update; apt-get install -y java-21-amazon-corretto-jdk

ENV LANG=C.UTF-8

ENV JAVA_HOME=/usr/lib/jvm/default-jvm
ENV PATH=$PATH:/usr/lib/jvm/default-jvm/bin


WORKDIR /app

COPY . .


# should already be built. Else this gets expensive.
# RUN chmod +x mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

#CMD ["ls", "-la", "target"]
 # TODO why am I having to put full name in here?
ENTRYPOINT ["java", "-jar", "target/whisper-wrapper-0.0.1-SNAPSHOT.jar"]