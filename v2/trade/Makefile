# GENERAL PATH DEFINITIONS
ROOT_DIR=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
DIST_DIR=$(ROOT_DIR)/dist
DOCKER_IMAGE="node:10.19-slim"
CC_NAME="trade"
CC_LANG="node"

BUILD_CMD=npm install && npm run build
UNITTEST_CMD=npm test

.PHONY: all
all: clean build package test

$(DIST_DIR):
	echo ">> Creating distribution directory"  
	mkdir -p $(DIST_DIR)

.PHONY: build
build: $(DIST_DIR)
	echo ">> Building chaincode"
	docker run  --rm -v $(ROOT_DIR):/src -v $(DIST_DIR):/dist -w /src ${DOCKER_IMAGE} sh -c "$(BUILD_CMD)"

.PHONY: package
package: $(DIST_DIR)
	echo ">> Packaging chaincode"
	peer lifecycle chaincode package dist/${CC_NAME}.tgz --label ${CC_NAME} --lang ${CC_LANG} --path .

.PHONY: test
test: 
	echo ">> Running chaincode unit-test"
	docker run  --rm -v $(ROOT_DIR):/src -v $(DIST_DIR):/dist -w /src ${DOCKER_IMAGE} sh -c "$(UNITTEST_CMD)"

.PHONY: clean
clean: 
	echo ">> cleaning dist directory"
	rm -rf $(ROOT_DIR)/dist
	rm -rf $(ROOT_DIR)/node_modules
	rm -rf $(ROOT_DIR)/coverage
	rm -rf $(ROOT_DIR)/.nyc_output
