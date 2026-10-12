FROM node:22-slim
ENV NODE_ENV=production \
    PORT=3000 \
    PF_DATA_DIR=/var/data
WORKDIR /app
COPY package.json server.js ./
COPY lib ./lib
COPY public ./public
RUN mkdir -p /var/data
EXPOSE 3000
CMD ["node", "--disable-warning=ExperimentalWarning", "server.js"]
