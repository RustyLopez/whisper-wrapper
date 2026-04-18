FROM amazoncorretto:21-alpine

# TODO get an image that is compatible with cuda, and intall cuda as per whisper docs.  https://github.com/m-bain/whisperX
# THOUGH, we may want two different dockerfiles. One for cuda envs and one for non.
RUN apk add --no-cache python3=~3.11 py3-pip

pip install whisperx

pip install pipx

pipx install insanely-fast-whisper

WORKDIR /app

COPY . .


RUN chmod +x mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/*.jar"]