# Use the [Choice] comment to indicate option arguments that should appear in VS Code UX. Use a comma separated list.
#
# [Choice] Debian OS version: bullseye, buster
ARG VARIANT="bullseye"
FROM buildpack-deps:${VARIANT}-curl

# Use the [Option] comment to specify true/false arguments that should appear in VS Code UX
#
# [Option] Install zsh
ARG INSTALL_ZSH="true"
# [Option] Upgrade OS packages to their latest versions
ARG UPGRADE_PACKAGES="false"
# Install needed packages and setup non-root user. Use a separate RUN statement to add your own dependencies.
# ARG USERNAME=vscode
# ARG USER_UID=1000
# ARG USER_GID=$USER_UID
# Install needed packages and setup non-root user. Use a separate RUN statement to add your own dependencies.
COPY library-scripts/*.sh library-scripts/*.env /tmp/library-scripts/
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && /bin/bash /tmp/library-scripts/common-debian.sh "${INSTALL_ZSH}" "${USERNAME}" "${USER_UID}" "${USER_GID}" "${UPGRADE_PACKAGES}" "true" "true" \
    # (mkdir is a workaround for a Debian install bug.)
    mkdir -p /usr/share/man/man1 \
    #
    # ****************************************************************************
    # * TODO: Add any additional OS packages you want included in the definition *
    # * here. We want to do this before cleanup to keep the "layer" small.       *
    # ****************************************************************************
    && apt-get -y install --no-install-recommends curl default-jre procps rlwrap redis-tools \
    #
    && apt-get autoremove -y && apt-get clean -y && rm -rf /var/lib/apt/lists/* /tmp/library-scripts
         
# Install Clojure
RUN curl -O https://download.clojure.org/install/linux-install-1.10.3.1058.sh \
	&& chmod +x linux-install-1.10.3.1058.sh \
	&& ./linux-install-1.10.3.1058.sh

# Install Leiningen
RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein \
	&& chmod +x lein \
	&& mv lein /usr/local/bin \
    && lein
RUN ln -s /workspace/.m2 /root/.m2

# Install Node
ENV HOME=/root
ENV NODE_VERSION=16.14.0
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
ENV NVM_DIR="${HOME}/.nvm"
RUN . "$NVM_DIR/nvm.sh" && nvm install ${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm use v${NODE_VERSION}
RUN . "$NVM_DIR/nvm.sh" && nvm alias default v${NODE_VERSION}
ENV PATH="${HOME}/.nvm/versions/node/v${NODE_VERSION}/bin/:${PATH}"

# Install shadow-cljs
WORKDIR /workspace
RUN npm install --save-dev shadow-cljs

# [Optional] Uncomment this section to install additional OS packages.
# RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
#     && apt-get -y install --no-install-recommends <your-package-list-here>
