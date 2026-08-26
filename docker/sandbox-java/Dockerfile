# Sandbox image for judged Java submissions. JDK, not JRE - the harness
# compiles the user's Solution.java + generated Main.java before running
# them, both inside this same container (see JavaRunner/JavaHarnessGenerator).
FROM eclipse-temurin:21-jdk-jammy

# Vendored at BUILD time, not judge-run time - sandbox containers run with
# NetworkMode:none (see the Phase 1 plan's resource-limits section), so
# anything the harness needs on the classpath has to already be baked into
# this image. org.json:json is the one dependency the generated harness's
# JSON parse/serialize glue needs - pinned version, not "latest", so a
# Maven Central release doesn't silently change what every future judged
# submission compiles against.
ADD https://repo1.maven.org/maven2/org/json/json/20251224/json-20251224.jar /opt/judge-libs/json.jar

# /sandbox must be writable by the non-root user - javac writes .class
# output there, and WORKDIR creates the directory as root before the USER
# switch below, so it needs an explicit chown or compilation fails with a
# permission error (caught by this image's own sanity check, not left as a
# surprise for the first real submission).
RUN useradd --no-create-home --shell /usr/sbin/nologin sandbox \
    && chmod -R a+r /opt/judge-libs \
    && mkdir -p /sandbox \
    && chown sandbox:sandbox /sandbox
WORKDIR /sandbox
USER sandbox

CMD ["sleep", "infinity"]
