# cos-meta-camel

## Running
```
docker run --rm -ti -p 9091:8080 quay.io/rhoas/cos-fleet-catalog-camel:0.0.1 
```

## Building
```
./mvnw clean install
docker build -t "quay.io/$USER/cos-fleet-catalog-camel:latest" -f cos-fleet-catalog-camel/Dockerfile cos-fleet-catalog-camel
```
And run your local copy

```
docker run --rm -ti -p 9091:8080 quay.io/$USER/cos-fleet-catalog-camel:latest 
```

or with https://github.com/svenstaro/miniserve

```
miniserve -v -p 9091 --index index.json cos-fleet-catalog-camel/static/
```
