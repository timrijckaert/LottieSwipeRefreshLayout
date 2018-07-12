# Lottie Pull To Refresh Layout

A custom `SwipeRefreshLayout` that shows a [Lottie View](https://github.com/airbnb/lottie-android) on top instead.

<img src="img/example.gif"></img>

## Basic usage

A `LottiePullToRefreshLayout` accepts only one child.
See example for more detail.

Tell `LottiePullToRefreshLayout` about your content view. `app:layout_type="content"`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<be.rijckaert.tim.lib.LottiePullToRefreshLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                       xmlns:app="http://schemas.android.com/apk/res-auto"
                                       android:id="@id/swipe_refresh"
                                       android:layout_width="match_parent"
                                       android:layout_height="match_parent"
                                       app:max_offset_top="250dp"
                                       app:pull_to_refresh_lottieFile="@raw/pull_to_refresh"
                                       app:trigger_offset_top="125dp">

    <android.support.v7.widget.RecyclerView
        android:id="@+id/recyclerView"
        app:layout_type="content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

</be.rijckaert.tim.lib.CustomPullToRefreshLayout>
```

## Attributes

`max_offset_top`: Maximum scroll area for the pull to refresh
`trigger_offset_top`: Cap the scroll area factor set a size that will tell when it has been fully pulled.
`pull_to_refresh_lottieFile`: The Lottie file to use. (placed in the raw folder)

## Extensible

You can use the base `SimplePullToRefreshLayout` to make your own pull to refresh view.

## Contributors
* [Simon Vergauwen (nomisRev)](https://github.com/nomisRev)
