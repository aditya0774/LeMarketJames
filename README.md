# LeMarketJames container

Minimal Java greeter packaged as a Docker container and built by Jenkins.

## Run locally

```powershell
docker build -t le-market-james .
docker run --rm --name le-market-james le-market-james
```

It should print `Hello from LeMarketJames!`.

## Spring Boot

Jenkins uses port `8080`, so the Spring Boot application uses port `8081`.

Run the application locally with:

```powershell
mvn spring-boot:run
```

Open `http://localhost:8081/` to view the greeting.

## Jenkins

Create a Pipeline job pointing at this repository and choose **Pipeline script from SCM**. The Linux Jenkins agent must have Java 21, Maven, Docker, and permission to run Docker commands. Jenkins will run the Maven unit test, build the image, run the greeter, verify its output, and clean up the image.

## Frontend notes



The `lemarket-ui` Angular app uses the [Zod](https://zod.dev) library for form validation (see `lemarket-ui/src/app/features/auth/register/register.schema.ts`). This dependency is pending approval from our architect.

### Running the register page

```powershell
cd lemarket-ui
npm install
npm start
```

Open `http://localhost:4200/register` to view the registration form.


LeBron is the goat 