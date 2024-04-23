import { StyleSheet, Text, View, type ColorValue } from 'react-native';
import React from 'react';
import TurboImage, { type CachePolicy } from 'react-native-turbo-image';

type Props = {
  size: number;
  title?: string;
  url: string;
  cachePolicy?: CachePolicy;
  priority?: number;
  rounded?: boolean;
  monochrome?: number | ColorValue;
  borderRadius?: number;
  blur?: number;
};
const Card = ({ title, size, ...props }: Props) => {
  return (
    <View style={styles.card}>
      <TurboImage
        style={[styles.image, { width: size, height: size }]}
        {...props}
      />
      {title && <Text style={styles.title}>{title}</Text>}
    </View>
  );
};

export default Card;

const styles = StyleSheet.create({
  card: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingBottom: 20,
  },
  image: {
    margin: 1,
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
  },
});