# Stetho-Volley
Volley Network Call Inspection library for Stetho.

Inspect your volley network requests without OkHttp Layer using Stetho Url-Connection.

# Usage

Archives are available in maven central. To use it, add the following lines in build.gradle
```gradle
dependencies {
   compile 'me.dlkanth:stetho-volley:1.0'
}
```
# Inspecting Volley Request

1) Add a new instance of `StethoVolleyStack` while creating `RequestQueue` for your volley request

```java
RequestQueue queue = Volley.newRequestQueue(this, new StethoVolleyStack());
```

That's all. Run the app and you can inspect your request and response from chrome://inspect/
