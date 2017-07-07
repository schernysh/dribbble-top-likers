# dribbble-top-likers
To run the program:
```
./gradlew run -PexecArgs="-clientAccessToken <clientAccessToken> -userId <userId> -topNum <topNum>"
```
where `<clientAccessToken>` is a client access token generated for an application registered in Dribbble and `<userId>` is an id of the user whose followers' likers to count. `<topNum>` is an optional parameter specifying how many top likers to output, default is 10.
