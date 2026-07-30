import { execSync } from 'child_process';
import * as path from 'path';

export default async function globalSetup() {
  const rootDir = path.resolve(__dirname, '..');
  console.log('\nBuilding Maven project (skipping tests)...');
  execSync('mvn install -DskipTests -q', { cwd: rootDir, stdio: 'inherit' });
  console.log('Maven build complete.\n');
}
