FROM nvcr.io/nvidia/pytorch:24.01-py3
# FROM python:3.11-alpine

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.11-venv \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN pip3 install pipx

RUN pip3 install insanely-fast-whisper --force #--pip-args="--ignore-requires-python"

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