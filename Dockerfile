FROM ubuntu:bionic

# Install additional OS packages.
RUN apt-get update \
	&& apt-get -y install curl procps rlwrap redis-server redis-tools

# Install Java
# (mkdir is a work-around for a Debian install bug.)
RUN mkdir -p /usr/share/man/man1 \
	apt-get update \
	&& apt-get -y install default-jre

# Install Clojure
RUN curl -O https://download.clojure.org/install/linux-install-1.10.3.1058.sh \
	&& chmod +x linux-install-1.10.3.1058.sh \
	&& ./linux-install-1.10.3.1058.sh

# Install Leiningen
RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
	&& chmod +x lein \
	&& mv lein /usr/local/bin \
        && lein

# Confiure Redis
RUN sed -i "s/bind .*/bind 127.0.0.1/g" /etc/redis/redis.conf

# Install Bookmarx
COPY * /opt/bookmarx/
WORKDIR /opt/bookmarx
RUN lein uberjar

# Start the services
ENTRYPOINT ["service", "redis-server", "start", ";"]
CMD ["java", "-jar", "target/bookmarx.jar", "server", ">", "/var/log/bookmarx.log", "2>&1"]
