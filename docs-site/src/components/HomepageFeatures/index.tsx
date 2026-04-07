import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  to: string;
  Svg: React.ComponentType<React.ComponentProps<'svg'>>;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Run The Stack',
    to: '/docs/getting-started/local-development',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        Start Postgres, boot Spring, run the Angular client, and work on the
        docs site without guessing where each piece lives.
      </>
    ),
  },
  {
    title: 'Understand The Backend',
    to: '/docs/architecture/backend-overview',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        See how the Spring Boot runtime is split across commands, sessions,
        services, world loading, and feature packages.
      </>
    ),
  },
  {
    title: 'Author Content',
    to: '/docs/content/world-data',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        See where rooms, skills, class stats, and player-facing messages live so
        content changes stay data-driven.
      </>
    ),
  },
];

function Feature({title, to, Svg, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <Link className={styles.featureCard} to={to}>
        <div className="text--center">
          <Svg className={styles.featureSvg} role="img" />
        </div>
        <div className="text--center padding-horiz--md">
          <Heading as="h3">{title}</Heading>
          <p>{description}</p>
          <span className={styles.featureLink}>Read section</span>
        </div>
      </Link>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
