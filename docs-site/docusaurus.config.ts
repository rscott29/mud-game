import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
  title: 'Mud Game Developer Docs',
  tagline: 'Java backend, Angular client, game systems, and content docs in one place',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true,
  },

  // Set the production url of your site here
  url: 'https://rscott29.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/mud-game/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'rscott29',
  projectName: 'mud-game',

  onBrokenLinks: 'throw',
  themes: ['@docusaurus/theme-mermaid'],
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl:
            'https://github.com/rscott29/mud-game/tree/main/docs-site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Mud Game Dev Docs',
      logo: {
        alt: 'Mud Game Docs logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Developer Docs',
        },
        {
          href: 'https://github.com/rscott29/mud-game',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'light',
      links: [
        {
          title: 'Developer Docs',
          items: [
            {
              label: 'Overview',
              to: '/docs/intro',
            },
            {
              label: 'Backend Overview',
              to: '/docs/architecture/backend-overview',
            },
            {
              label: 'Local Development',
              to: '/docs/getting-started/local-development',
            },
          ],
        },
        {
          title: 'Project',
          items: [
            {
              label: 'Repository',
              href: 'https://github.com/rscott29/mud-game',
            },
            {
              label: 'World Data',
              to: '/docs/content/world-data',
            },
            {
              label: 'Room Map',
              to: '/docs/content/world-map',
            },
          ],
        },
        {
          title: 'Systems',
          items: [
            {
              label: 'Movement And Travel',
              to: '/docs/game-systems/movement-resting-and-travel',
            },
            {
              label: 'Quests And World State',
              to: '/docs/game-systems/quests-and-world-state',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Mud Game. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java'],
    },
    mermaid: {
      theme: {
        light: 'neutral',
        dark: 'dark',
      },
      options: {
        flowchart: {
          useMaxWidth: false,
        },
        themeVariables: {
          fontSize: '18px',
        },
      },
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
