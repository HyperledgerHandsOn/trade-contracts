# GENERAL PATH DEFINITIONS
ROOT_DIR=$(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
DIST_DIR=$(ROOT_DIR)/dist
DOCKER_IMAGE_BUILD="gradle:jdk11"
DOCKER_IMAGE_PACKAGE="hyperledger/fabric-tools:2.0.1"
PACKAGE_COMMAND=peer lifecycle chaincode package
CC_NAME="exportLicense"
CC_LANG="java"

BUILD_CMD=gradle build
UNITTEST_CMD=gradle test

.PHONY: all
all: clean build package test

$(DIST_DIR):
	echo ">> Creating distribution directory"  
	mkdir -p $(DIST_DIR)

.PHONY: build
build: $(DIST_DIR)
	echo ">> Building chaincode"
	docker run --rm -v $(ROOT_DIR):/home/gradle/project -w /home/gradle/project $(DOCKER_IMAGE_BUILD) $(BUILD_CMD)

.PHONY: package
package: $(DIST_DIR)
	echo ">> Packaging chaincode"
	docker run --rm  -v $(ROOT_DIR):/src -w /src $(DOCKER_IMAGE_PACKAGE) $(PACKAGE_COMMAND) dist/$(CC_NAME).tgz --label $(CC_NAME) --lang $(CC_LANG) --path .

.PHONY: test
test: 
	echo ">> Running chaincode unit-test"
	docker run --rm -v $(ROOT_DIR):/home/gradle/project -w /home/gradle/project $(DOCKER_IMAGE_BUILD) $(UNITTEST_CMD)

.PHONY: clean
clean: 
	echo ">> Cleaning dist directory"
	rm -rf $(ROOT_DIR)/bin
	rm -rf $(ROOT_DIR)/dist
	rm -rf $(ROOT_DIR)/build
