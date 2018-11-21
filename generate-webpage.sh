#!/bin/sh

IFS=$'\n'
PUBLIC=${1:-build}

for I in ./gradle.properties ./build/gitversion/*version.properties; do
	eval $(cat ${I} | tr '"' "'" | sed -e '/^\s*#/d' -e 's/^\([^=]*\)=\(.*\)$/\1="\2"/g')
done

function makeLink() {
	SPACE=${1}
	shift
	for I in $*; do
		BASENAME=${I##*/}
		echo "${SPACE}<li><a href=\"${I}\">${BASENAME}</a></li>"
	done
}

cd "${PUBLIC}"

cat <<END
<html>
<head>
	<title>${externalProjectName} version ${versionName}</title>
</head>
<body>
	<h1>${externalProjectName} version ${versionName}</h1>
	<p>
		<a href="${CI_PROJECT_URL}/tree/${CI_COMMIT_REF_NAME}">${CI_COMMIT_REF_NAME}</a> build
		on <emph>$(date -R)</emph>,
		commit <a href="${CI_PROJECT_URL}/commit/${CI_COMMIT_SHA}">${CI_COMMIT_SHA}</a>
	</p>
	<h2>Distribution</h2>
	<ul>
$(makeLink "		" ./distributions/*)
	</ul>
	<h2>Development</h2>
	<ul>
		<li><a href="${CI_PROJECT_URL}">source-code repository</a><br /><code>git clone ${CI_PROJECT_URL}.git</code></li>
		<li><a href="./javadoc/">JavaDoc documentation</a></li>
		<li>Maven repositories:
			<ul>
				<li><a href="./mvn-repo">stable/latest only</a></li>
				<li><a href="https://gitlab.com/api/v4/projects/${CI_PROJECT_ID}/packages/maven/">development builds</a>
					(<a href="https://gitlab.com/api/v4/projects/${CI_PROJECT_ID}/packages/maven/${externalGroup//.//}/${externalProjectName}/maven-metadata.xml">maven-metadata.xml</a>)
				</li>
			</ul>
		</li>
	</ul>
	<h2>Usage</h2>
	<pre>$(cat help.txt | tr -d '<>&')</pre>
	<h2>License</h2>
	<pre>$(cat LICENSE | tr -d '<>&')</pre>
</body>
</html>
END
