with (import <nixpkgs> {});

mkShell {
  buildInputs = [
    influxdb
    jdk17_headless
    sbt
  ];
  shellHook = ''
    export YTL_HOME=`pwd`
    export ANSIBLE_HOST_KEY_CHECKING=False
    export ANSIBLE_INVENTORY=inventory.yaml
    export PATH="$PWD/node_modules/.bin/:$PATH"
  '';
}
