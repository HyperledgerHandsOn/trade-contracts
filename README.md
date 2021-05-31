[![Build Status](https://travis-ci.com/HyperledgerHandsOn/trade-contracts.svg?branch=master)](https://travis-ci.com/HyperledgerHandsOn/trade-contracts)  
# Trade-contracts

This repository contains the various smart contracts of the Trade Network.

There are two versions represented by the folders:  
* `v1` - Used from chapter 4 through 8
* `v2` - Used to represent the addition of a new organization, part of chapter 9. 

Under the respective version folder you will find the four smart contracts:  

* Trade -- folder `/trade`
* Shipment -- folder `/shipment`
* Letter of Credit -- folder `/letterOfCredit`
* Export License -- folder `/exportLicense`

To ensure that VSCode properly identify each projects as a distinct smart contract, we recommend that you import each one separately into the workspace.

## Pre requisites
   
Code has been tested on macOS Catalina and Ubuntu 18.04.  
While it should run on other platform like Windows, it does not include the convenience scripts.

| Component | Version |  
|-----------|---------|  
| git | latest |  
| jq | latest |  
| docker | 19.03-ce or greater is required |
| docker-compose | 1.25.0 or greater installed |
| node | 12.13.1+ |
| npm | 6.12 and above |
| Java | JDK 11 |
| Gradle | 6.4+ |
| make | latest |
| g++ | 11.0.0 |

**Note:** When running the full network and smart contracts, Docker on Windows or Mac requires 8GB allocated to it.  You can change this from the Docker config panel (Preferences -> Advanced)  
  
## Building the Smart Contracts  
  
A convenience shell script is provided in the root of this repository:
`./makeAll.sh`  
  
This script will invoke `make all` within each sub-repository. This will build, run the tests and package the smart contracts.  

Manual build of the **Java** chaincode(exportLicense/letterOfCredit):  
  
1. Change directory (`cd`) into the chaincode you want to build.  
2. Run `gradle build`  
3. To test the chaincode run `gradle test`
  
Manual build of the **JavaScript** chaincode(shipment/trade):  
  
1. Change directory (`cd`) into the REST server you want to build.  
2. Run `npm install`  
3. To test the REST server run `npm test`

## Deploying smart contracts  
  
The smart contracts (aka chaincode) is deployed via the `trade-network` project.
