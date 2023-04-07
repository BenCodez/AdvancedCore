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
