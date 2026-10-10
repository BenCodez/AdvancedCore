# AdvancedCore
API used in the plugins developed by BenCodez, can be used in any project.

[Download](https://www.spigotmc.org/resources/advancedcore.28295/)

## License
### This project is licensed under the Creative Commons Attribution 3.0 Unported license.
[Read it here](https://creativecommons.org/licenses/by/3.0/)

## How to use
### Use the following code in Maven:
    <repository>
	    <id>BenCodez Repo</id>
	    <url>https://nexus.bencodez.com/repository/maven-public/</url>
    </repository>

    <dependency>
        <groupId>com.bencodez</groupId>
	    <artifactId>advancedcore</artifactId>
	    <version>LATEST</version>
	    <scope>provided</scope>
    </dependency>

  ### In Gradle:
    repositories {
        maven { url "https://nexus.bencodez.com/repository/maven-public/" }
    }
    dependencies {
        compile "com.bencodez:advancedcore:LATEST"
    }
  
  Versions:  
  LATEST - latest stable release  
  Check out all tags [on the releases tab](https://github.com/BenCodez/AdvancedCore/tags).


Calendar checks requested by reload run on the existing background calendar timer,
so asynchronous date events are not called inline from a Bukkit or Folia owner.
Repeated requests coalesce while one check is queued or running. Shutdown waits
for admitted checks; queued callbacks skip a retired checker or replaced timer.
Normal periodic checks remain unchanged. This does not move the remaining reload
file reads off the caller, or make reload completion await all date-event listeners.
