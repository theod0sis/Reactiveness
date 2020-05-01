# Reactiveness

Reactiveness is an Intellij plugin that provides the capability of automate refactoring Android 
AsyncTask code to Reactive Programming. 

## AsyncTask
AsyncTask for many years was an easy way for Android programmers to handle short operations without freezing the UI, 
dealing with threads. An [AsyncTask](https://developer.android.com/reference/android/os/AsyncTask) is defined by a computation
that runs on a background thread and whose result is published on the UI thread. Due to lot of drawbacks, AsyncTask was deprecated
in Android  API level R.

##Reactive Extension
[Reactive Extension](http://reactivex.io/intro.html) (aka Rx or ReactiveX) is a library for creating asynchronous and event base
applications. ReactiveX is a combination of observer and iterator design patterns. Merge the advantage of iterators with 
the flexibility of event based asynchronous programming.    
Rx libraries for well-known programming languages and platforms:  

   - [RxJava](https://github.com/ReactiveX/RxJava)
   - [RxJs](https://github.com/ReactiveX/rxjs)
   - [RxScala](https://github.com/ReactiveX/RxScala)
   - [RxAndroid](https://github.com/ReactiveX/RxAndroid)
   - [RxNetty](https://github.com/ReactiveX/RxNetty)

Rx offers an easy way to handle the thead where each step will run. 
By default, an Observable and the chain of operators that you apply to it will do its work, and will notify its observers, 
on the same thread on which its Subscribe method is called. The SubscribeOn operator changes this behavior by specifying a 
different Scheduler on which the Observable should operate. The ObserveOn operator specifies a different Scheduler that 
the Observable will use to send notifications to its observers. 

Observable will run on the Ui thread:

![](/img/observeOn1.gif)

Observable will run on the blue thread due to SubscribeOn(blue): 

![](/img/observeOn.png)

## How to run local:

```
      gradlew buildPlugin
      gradlew runIde
```

## Useful info
The plugin refactors only inner AsyncTask classes on the current state.