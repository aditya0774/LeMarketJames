# LeMarketJames container

Minimal Java greeter packaged as a Docker container and built by Jenkins.

## Run locally

```powershell
docker build -t le-market-james .
docker run --rm --name le-market-james le-market-james
```

It should print `Hello from LeMarketJames!`.

## Jenkins

Create a Pipeline job pointing at this repository and choose **Pipeline script from SCM**. The Jenkins agent must have Docker and permission to run Docker commands. Jenkins will build the image, run the greeter, verify its output, and clean up the image.