# Dependency Runtime Maven Plugin

Adding this plugin to your project allows you to put just your code in a jar
file, then all of the dependencies will be downloaded from Maven when they are
needed at runtime.

If you are interested in the code that makes this possible, you can view the
library [here](https://github.com/zachdeibert/maven-dependency-runtime).

## Usage

Add the following to your project's `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.github.zachdeibert</groupId>
            <artifactId>dependency-runtime-maven-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>inject-runtime</id>
                    <goals>
                        <goal>inject-runtime</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <runtimeVersion>1.0.0-SNAPSHOT</runtimeVersion>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Note: this requires your jar to have the `Main-Class` attribute set.
If you want this plugin to set the attribute for you, add
`<mainClass>your.main.Class</mainClass>` to the `<configuration>` tag.

## Legal

This project has the MIT license, so you can use the code for your project,
regardless of what your project is or what license your project has.
There is a copyright notice for my code embedded in the jar, so all you need to
do is install the plugin, then you are free to distribute it.
