# cos-meta-camel

## Building
```
./mvnw clean install
docker build -t "quay.io/lburgazzoli/ccs:latest" -f cos-fleet-catalog-camel/Dockerfile cos-fleet-catalog-camel
```

## Running
```
docker run --rm -ti -p 9091:80 quay.io/lburgazzoli/ccs:latest 
```

or with https://github.com/svenstaro/miniserve

```
miniserve -v -p 9091 --index index.json cos-fleet-catalog-camel/static/
```
