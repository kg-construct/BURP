async function loadTurtle() {
  //this is the function you call in 'preProcess', to load the highlighter
  const worker = await new Promise(resolve => {
    require(["core/worker"], ({ worker }) => resolve(worker));
  });
  const action = "highlight-load-lang";
  const langURL =
    "https://cdn.jsdelivr.net/gh/redmer/highlightjs-turtle/src/languages/turtle.js";
  const propName = "hljsDefineTurtle"; // This funtion is defined in the highlighter being loaded
  const lang = "turtle"; // this is the class you use to identify the language
  worker.postMessage({ action, langURL, propName, lang });
  return new Promise(resolve => {
    worker.addEventListener("message", function listener({ data }) {
      const { action: responseAction, lang: responseLang } = data;
      if (responseAction === action && responseLang === lang) {
        worker.removeEventListener("message", listener);
        resolve();
      }
    });
  });
}

var respecConfig = {
  // check https://respec.org/docs/ for the meaning of these keys
  preProcess: [loadTurtle],
  authors: [
    {
      name: "Pano Maria",
      mailto: "pano@skemu.com",
      url: "https://panomaria.com",
      company: "Skemu",
      companyURL: "https://skemu.com"
    },
    {
      name: "Els de Vleeschauwer",
      mailto: "els.devleeschauwer@ugent.be",
      company: "Ghent University &ndash; imec &ndash; IDLab",
      orcidid: "0000-0002-8630-3947",
      companyURL: "https://idlab.technology/"
    }
  ],
  edDraftURI: "https://w3id.org/rml/lv/test-cases/",
  editors: [
    {
      name: "Pano Maria",
      mailto: "pano@skemu.com",
      url: "https://panomaria.com",
      company: "Skemu",
      companyURL: "https://skemu.com"
    },
    {
      name: "Els de Vleeschauwer",
      mailto: "els.devleeschauwer@ugent.be",
      company: "Ghent University &ndash; imec &ndash; IDLab",
      orcidid: "0000-0002-8630-3947",
      companyURL: "https://idlab.technology/"
    }
  ],
  formerEditors: [
  ],
  github: "https://github.com/kg-construct/rml-lv",
  license: "w3c-software-doc",
  localBiblio: {
    "RML-Core": {
      title: "RML-Core",
      href: "https://w3id.org/rml/core/spec",
      status: "Draft Community Group Report",
      publisher: "W3C",
      date: "07 August 2024",
    },
    "RML-IO": {
      title: "RML-IO",
      href: "https://w3id.org/rml/io/spec",
      status: "Draft Community Group Report",
      publisher: "W3C",
      date: "12 March 2024",
    },
  },
  otherLinks: [],
  shortName: "RML-LV-Testcases",
  specStatus: "CG-DRAFT",
  // W3C config
  copyrightStart: "2024",
  doJsonLd: true,
  group: "kg-construct",
};
