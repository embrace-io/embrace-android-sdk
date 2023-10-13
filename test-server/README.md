## Testing

### Basic Lambda types

These are important objects that will come up later for interacting with the TestServer. The order displayed is the order they would be invoked for a given request.

```
where "T" is the object (`Session`, `Event` etc) in the Request body, and "R" is the object (`Config` or Unit for the rest) in the Response body

typealias RequestFilter<T> = (T, EmbraceMockConnectionImpl?) -> Boolean
typealias ResponseLogic<T, R> = (T, EmbraceMockConnectionImpl) -> Response<R>
typealias OnRequestCallback<T, R> = (T, Response<R>, EmbraceMockConnectionImpl) -> Unit
```

#### RequestFilter

`RequestFilter` is used to indicate which request you would like this to be applied to. The way this should be implemented is, in your lambda, inspect the Request object and/or the connection to see if it meets the criteria you are testing for, and if it does return `true`, and if it doesn't, return `false`

**In almost all cases, a RequestFilter of `null` will match ALL requests for an endpoint**

#### ResponseLogic

`ResponseLogic` is used to generate Responses given a Request. The TestServer will invoke the `ResponseLogic` instance with the request it passed from the `UrlConnection` and set the values in the returned Response object on the `UrlConnection`

#### OnRequestCallback

`OnRequestCallback` is invoked after a request goes out and a response is generated, but before that response is passed to the connection, so the SDK has not received the response yet. This callback is designed to be where you can put assertations, probably assertations confirming that the SDK sent out the proper request object.


### `TestServer`

```
fun <T, R> getEndpointLogic(endpointType: EndpointType<T, R>): EndpointLogic<T, R>
fun <T, R> getEndpointTesting(endpointType: EndpointType<T, R>): EndpointTesting<T, R>

val receivedRequests: List<ReceivedRequests<out Any?, out Any?>>
```

`TestServer` is an in process mock server that receives the raw `UrlConnection` object and injects/mocks/sets a response based on internal set of "logic" that it carries.

This class is composed of multiple `Endpoint` instances, one each for "Config", "Events", "Sessions" and "Images". Each Endpoint has a corresponding `EndpointType`, which is used to fetch the instance. logic is registered on a per-endpoint basis, and the `TestServer` will delegate the responsibility of processing a request to an `Endpoint` based on the URL of the request.

to fetch the "Events" `Endpoint` from `TestServer` call;

```
// to retrieve the endpoint logic
EndpointLogic<Event, Unit> endpoint = mServer.getEndpointLogic(EndpointType.Events)

//to retrieve the config testing methods
EndpointTesting<Unit, Config> endpoint = mServer.getEndpointTesting(EndpointType.Events)
```

`receivedRequests` will return all the requests received for all the Endpoints in one List

#### `Endpoint`

`Endpoint` is a single implementation, but is broken up into 2 interfaces, `EndpointLogic` which contains setters for response "logic" and `EndpointTesting` which contains methods and callbacks to expose the internal state of the server, useful for writing tests. No huge reason for doing this, I just thought it would make it simpler

#### `EndpointLogic`

```
fun addRequestResponseLogic(filter: RequestFilter<T>?, logic: ResponseLogic<T, R>)
fun clearRequestResponseLogic()
```

`addRequestResponseLogic` will add a conditional response for an Endpoint. First the Endpoint will check if the incoming request gets `true` from your `RequestFilter` and if so, it will generate a Response based on the value returned by the `ResponseLogic`. **TestServer will check for a match starting with the most recent entry** and working backward chronologically from there, and will only generate a response based on the first match. So, even if you have multiple matching `RequestFilter`s for a given request, it will only use the most recent one's `ResponseLogic` to generate a Response

Each endpoint has default "happy" logic. basically for Config it's to just return a default config object and the rest to return a 200 regardless of the request body. `clearRequestResponseLog()` will remove all of the logic set during the test and revert back to that default logic; the same behavior you would get if you had not added any logic in the first place


#### `EndpointTesting`

```
val receivedRequests: List<ReceivedRequests<T, R>>
fun onRequestFinished(requestFilter: RequestFilter<T>? = null, onRequestCallback: OnRequestCallback<T, R>? = null)
fun onRequestFinishedBlocking(requestFilter: RequestFilter<T>? = null, onRequestCallback: OnRequestCallback<T, R>? = null)
fun clearRequestFilter()
```

`receivedRequests` is a list of all the requests received during the test run for the endpoint The current request will already be added to this by the time an `OnRequestReceived` callback is invoked, keep that in mind if you are using this field in the body of an `OnRequestReceived` lambda. Also, from java this is transformed into a method, `getReceivedRequests()` as part of the standard interop

`onRequestFinished()` allows you to register a filter and a callback for a request. **This callback will only fire once**, after a match is received and the functions are invoked, it will be removed from the TestServer.

> I couldn't quite decide what the default behavior should be, or if there should be an option to leave it up indefinitely or return a boolean in the `OnRequestCallback` which indicates if it should be unregistered or not...idk, lots of possibilities with this and the behavior I chose is just based on personal preference. Lmk if you want this changed in the future

`onRequestFinishedBlocking()` take the filter and callback and **block the thread until a match is received**. This kind of method is super useful if the completion of a test hinges on checking what the body of a network request looks like, or making sure a request went out. you can make this the last method in a test, and it will either fail or continue and complete the test depending on what happens.

There is a "timeout` that can be set by calling `TestServer.defaultTimeout`. This method will block only as long as the `defaultTimeout` period, and if a match hasn't been found **the test will fail**

`clearRequestFilter()` removes all these filters

### Base Test Classes

Tests should extend 1 of the 2 current Base Test classes. The classes will do all the messy resetting and rewiring necessary for the `TestServer` to work, and stepping outside of them will take some work to get running

`BaseTest` just does basic setup of the testing environment, you will need to call `Embrace.start()` yourself to start the SDK

`BaseEmbraceStarted` extends `BaseTest` but also starts the SDK and fires the `OnCreate` and `OnStart` lifecycle methods so a Session is already underway by the time your test starts running

#### `BaseTest`/`EmbraceContext `

`BaseTest`/`EmbraceContext`
```
fun setBuildInfo(buildInfo: BuildInfo)
fun setLocalConfig(localConfig: LocalConfig)

fun triggerLifecycleEvent(event: Lifecycle.Event)
fun triggerOnLowMemory()

fun sendForeground()
fun sendBackground()
```

We have full control over mocking the activity lifecycle. This applies both for callbacks registered to `Context.registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks)` as well as the generated `LifecycleObserver`s registered via `ProcessLifecycleOwner.get().getLifecycle().addObserver(LifecycleObserver)`. There aren't really any guardrails on this to prevent setting the lifecycles out of order or anything, so it is possible that the Android internals will not like it if you call lifecycle methods out of order. If you are simply trying to send the application into the foreground or the background, you should use `sendForeground()` or `sendBackground()` instead

> The hacking I did to get override the observer registered with `ProcessLifecycleOwner` seems solid, but hasn't been tested in a wide variety of situations, so I could see it breaking.  Lmk if it does

`setBuildInfo(BuildInfo)` and `setLocalConfig(LocalConfig)` both inject the objects into the `resources` folder which will be fetched on startup, so **these methods must be called before `Embrace.start()` to have an effect`**

Also, All of these methods are available in the `EmbraceContext` instance, calling them there will have the exact same effect as calling them on `BaseTest`

`sendForeground()` will trigger the `ON_START` lifecycle event to occur. This also includes all preceeding lifecycle events. For example, if the test has not called triggerLifecycleEvent() yet and this method is invoked, it will call `triggerLifecycleEvent(Lifecycle.Event.ON_CREATE)`, then call `triggerLifecycleEvent(Lifecycle.Event.ON_START)`.

`sendBackground()` will trigger the `ON_STOP` lifecycle event to occur. Much like the previous method, all preceeding lifecycle events will be triggered
