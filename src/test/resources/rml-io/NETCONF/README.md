# NETCONF Server Instructions

First, deploy the NETCONF server using a containerized version of netooper2:

```bash
docker run -it -d -p 830:830 --name netopeer2-server sysrepo/sysrepo-netopeer2:latest
```

The NETCONF server listens locally on port 830 and runs a default configuration with username `netconf` and password `netconf`.

After this, the possible RML mappings to transform the YANG Library 1.0 into RDF can be tested against the server:

- [Subtree filter query](./subtree/mapping.ttl)
- [XPath filter query](./xpath/mapping.ttl)
