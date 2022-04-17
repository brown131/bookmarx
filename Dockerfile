# brown131/bookmarx
# docker run -d -p 3449:3449 -v `pwd`/redis:/var/lib/redis -p `pwd`/log:/var/log --restart unless-stopped --name bookmarx brown131/bookmarx:latest
FROM ubuntu:bionic

# Install additional OS packages.
RUN apt-get update \
	&& apt-get -y install curl procps rlwrap redis-tools

# Install Java
# (mkdir is a workaround for a Debian install bug.)
RUN mkdir -p /usr/share/man/man1 \
	&& apt-get update \
	&& apt-get -y install default-jre

# Install Clojure
RUN curl -O https://download.clojure.org/install/linux-install-1.11.1.1105.sh \
	&& chmod +x linux-install-1.11.1.1105.sh \
	&& ./linux-install-1.11.1.1105.sh

# Install Leiningen
RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
	&& chmod +x lein \
&& mv lein /usr/local/bin \
        && lein

# Install Bookmarx
COPY target/bookmarx.jar /opt/bookmarx/

# Start the services
EXPOSE 3449
WORKDIR /opt/bookmarx
CMD sleep 5;java -jar bookmarx.jar >> /var/log/bookmarx.log
